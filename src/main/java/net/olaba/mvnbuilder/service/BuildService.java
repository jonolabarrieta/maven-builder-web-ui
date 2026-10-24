package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.entities.BuildRunType;
import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.model.BuildFailure;
import net.olaba.mvnbuilder.model.LogMessage;
import net.olaba.mvnbuilder.model.CommandResult;
import net.olaba.mvnbuilder.model.ActionSummary;
import net.olaba.mvnbuilder.model.BuildRangeMode;

import net.olaba.mvnbuilder.repository.BuildProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for executing build and Git operations on Maven projects.
 */
@Service
@RequiredArgsConstructor
public class BuildService {

    private final ProcessExecutionService processExecutionService;
    private final WorkspaceService workspaceService;
    private final BuildProfileRepository buildProfileRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final SystemSettingService systemSettingService;
    private final NotificationService notificationService;
    private final BuildHistoryService buildHistoryService;
    private final BuildContinuationRegistry continuations = new BuildContinuationRegistry();
    private final java.util.Set<String> activeRuns = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> canceledRuns = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Resolves the workspace shared by the affected projects, if any.
     *
     * @param projects the affected projects
     * @return the workspace or null
     */
    private Workspace workspaceOf(final List<MavenProject> projects) {
        return projects.stream()
                .filter(project -> project != null && project.getWorkspace() != null)
                .map(MavenProject::getWorkspace)
                .findFirst()
                .orElse(null);
    }

    /**
     * Persists and broadcasts an action summary using the workspace of the
     * affected projects for context.
     *
     * @param summary  the action summary
     * @param projects the affected projects
     */
    private void publishSummary(final ActionSummary summary, final List<MavenProject> projects) {
        final MavenProject context = projects.stream()
                .filter(project -> project != null && project.getWorkspace() != null)
                .findFirst()
                .orElse(null);
        notificationService.publish(summary,
                context == null ? null : context.getWorkspace().getId(),
                context == null ? null : context.getWorkspace().getName());
    }

    /**
     * Retrieves the command arguments from the active build profile.
     * 
     * @return An array of command arguments.
     */
    private String[] getActiveProfileCommand() {
        return buildProfileRepository.findByIsDefaultTrue()
                .map(profile -> profile.getCommand().split("\\s+"))
                .orElse(new String[] { "-B", "clean", "install" });
    }

    /**
     * Returns the appropriate Maven executable command based on the operating system.
     * 
     * @return "mvn.cmd" for Windows, "mvn" otherwise.
     */
    private String getMvnCommand() {
        return System.getProperty("os.name").toLowerCase().contains("win") ? "mvn.cmd" : "mvn";
    }

    /**
     * Builds a single Maven project if it is enabled.
     * 
     * @param project The project to build.
     */
    public void buildProject(final MavenProject project) {
        if (!project.isMaven()) throw new IllegalArgumentException("This operation requires a Maven project.");
        if (!project.isEnabled()) {
            messagingTemplate.convertAndSend("/topic/logs",
                    new LogMessage(project.getDisplayName(), "SKIPPING: Project is disabled."));

            return;
        }
        final File projectDir = getProjectDir(project);
        final String[] args = getActiveProfileCommand();
        final String[] fullCmd = new String[args.length + 1];
        fullCmd[0] = getMvnCommand();
        System.arraycopy(args, 0, fullCmd, 1, args.length);
        final String javaHome = getJavaHomeForWorkspace(project.getWorkspace());
        final Workspace workspace = project.getWorkspace();
        final BuildHistoryService.BuildRunRecorder recorder = buildHistoryService.start(
                workspace == null ? null : workspace.getId(),
                workspace == null ? null : workspace.getName(),
                BuildRunType.PROJECT);
        processExecutionService.executeCommandWithJavaHome(project.getDisplayName(), projectDir, javaHome, fullCmd)
                .thenAccept(result -> {
                    final boolean success = result.getExitCode() == 0;
                    recorder.projectBuilt(project.getDisplayName(), result.getDurationMs(), success);
                    buildHistoryService.finish(recorder, success);
                    final ActionSummary summary = ActionSummary.builder()
                            .withAction("build")
                            .withSuccess(success)
                            .withFailedProject(success ? null : project.getDisplayName())
                            .withSucceededProjects(success ? List.of(project.getDisplayName()) : List.of())
                            .build();
                    publishSummary(summary, List.of(project));
                });
    }

    /**
     * Builds a list of projects sequentially, skipping disabled ones.
     * 
     * @param projects The list of projects to build.
     */
    public void buildProjectsSequentially(final List<MavenProject> projects) {
        buildProjectsSequentially(projects, BuildRunType.SELECTION);
    }

    /**
     * Builds a list of projects sequentially, skipping disabled ones, and records
     * a build run of the given type.
     * 
     * @param projects The list of projects to build.
     * @param type     The recorded run type.
     */
    public void buildProjectsSequentially(final List<MavenProject> projects, final BuildRunType type) {
        Workspace workspace = workspaceOf(projects);
        String[] args=getActiveProfileCommand();
        java.util.ArrayList<String> command=new java.util.ArrayList<>(); command.add(getMvnCommand()); command.addAll(List.of(args));
        List<String> excluded=projects.stream().filter(p->!p.isMaven()).map(MavenProject::getDisplayName).toList();
        executePlan(projects.stream().filter(MavenProject::isMaven).distinct().toList(), type, command,
                getJavaHomeForWorkspace(workspace), excluded, workspace);
    }

    private void executePlan(List<MavenProject> projects, BuildRunType type, List<String> command,
            String javaHome, List<String> omitted, Workspace workspace) {
        if (projects.stream().noneMatch(MavenProject::isEnabled)) {
            messagingTemplate.convertAndSend("/topic/logs",new LogMessage("Build", "No remaining Maven projects to build."));
            return;
        }
        String runId=java.util.UUID.randomUUID().toString(); activeRuns.add(runId);
        Long workspaceId=workspace==null?null:workspace.getId();
        String workspaceName=workspace==null?null:workspace.getName();
        var recorder=buildHistoryService.start(workspaceId,workspaceName,type);
        final List<String> succeeded=new java.util.ArrayList<>();
        final java.util.concurrent.atomic.AtomicReference<String> failedName=new java.util.concurrent.atomic.AtomicReference<>();
        final List<String> skipped=new java.util.ArrayList<>(omitted);
        for (String name:omitted) messagingTemplate.convertAndSend("/topic/logs",new LogMessage("Build","SKIPPING: " + name + " (omitted from this run)."));
        CompletableFuture<Integer> future=CompletableFuture.completedFuture(0);
        for (int i=0;i<projects.size();i++) {
            int index=i; MavenProject project=projects.get(i);
            future=future.thenCompose(previous->{
                if (previous!=0 || canceledRuns.contains(runId)) return CompletableFuture.completedFuture(previous==0?-1:previous);
                if (!project.isEnabled()) {
                    skipped.add(project.getDisplayName());
                    messagingTemplate.convertAndSend("/topic/logs",new LogMessage(project.getDisplayName(),"SKIPPING: Project is disabled."));
                    return CompletableFuture.completedFuture(0);
                }
                return processExecutionService.executeCommandWithJavaHome(project.getDisplayName(),getProjectDir(project),javaHome,command.toArray(String[]::new))
                    .exceptionally(error->new CommandResult(-1,List.of(error.getMessage()==null?"Build failed":error.getMessage()),0))
                    .thenApply(result->{
                        int code=result.getExitCode(); recorder.projectBuilt(project.getDisplayName(),result.getDurationMs(),code==0);
                        if (code!=0) {
                            failedName.set(project.getDisplayName());
                            List<MavenProject> pending=projects.subList(index,projects.size());
                            String continuationId=null;
                            if (!canceledRuns.contains(runId)) continuationId=continuations.register(new BuildContinuationRegistry.Plan(
                                workspaceId,runId,pending.stream().map(p->new BuildContinuationRegistry.Target(p.getId(),getProjectDir(p).getAbsolutePath())).toList(),command,javaHome,skipped));
                            notificationService.publishBuildFailure(new BuildFailure(project.getDisplayName(),project.getId(),pending.stream().map(MavenProject::getId).toList(),workspaceId,runId,continuationId),workspaceId,workspaceName);
                        } else succeeded.add(project.getDisplayName());
                        return code;
                    });
            });
        }
        future.whenComplete((code,error)->{
            boolean success=error==null && code==0 && !canceledRuns.contains(runId);
            try {
                buildHistoryService.finish(recorder,success);
                ActionSummary summary=ActionSummary.builder().withAction("build").withSuccess(success)
                    .withWorkspaceId(workspaceId).withRunId(runId).withSkippedProjects(List.copyOf(skipped))
                    .withSucceededProjects(List.copyOf(succeeded)).withFailedProject(failedName.get()).build();
                publishSummary(summary,projects);
            } finally { activeRuns.remove(runId); canceledRuns.remove(runId); }
        });
    }

    public record ContinuationResult(String state, String message) { }
    public ContinuationResult continueBuild(Long workspaceId,String continuationId,String mode) {
        final java.util.Map<Long,MavenProject> current=workspaceService.getProjectsForWorkspace(workspaceId,false).stream()
            .collect(java.util.stream.Collectors.toMap(MavenProject::getId,p->p));
        BuildContinuationRegistry.Plan plan=continuations.accept(workspaceId,continuationId,mode,value->{
            for (var target:value.pending()) {
                MavenProject project=current.get(target.id());
                if (project==null || project.getWorkspace()==null || !workspaceId.equals(project.getWorkspace().getId())
                        || !project.isMaven() || !getProjectDir(project).isDirectory() || !new File(getProjectDir(project),"pom.xml").isFile()
                        || !getProjectDir(project).getAbsolutePath().equals(target.directory()))
                    throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"The original build queue changed or is unavailable. Review the workspace and start a fresh build.");
            }
        });
        List<MavenProject> pending=plan.pending().stream().map(t->current.get(t.id())).toList();
        List<String> skipped=new java.util.ArrayList<>(plan.skipped());
        if (mode.equals("skip")) { skipped.add(pending.getFirst().getDisplayName()); pending=pending.subList(1,pending.size()); }
        if (pending.stream().noneMatch(MavenProject::isEnabled)) {
            String message="No remaining projects. Skipped: " + String.join(", ",skipped) + ". The original build remains failed.";
            messagingTemplate.convertAndSend("/topic/logs",new LogMessage("Build",message));
            Workspace workspace=current.get(plan.pending().getFirst().id()).getWorkspace();
            notificationService.recordLocal(new net.olaba.mvnbuilder.model.LocalNotificationRequest("info","No remaining projects",message,workspaceId,workspace.getName()));
            return new ContinuationResult("empty",message);
        }
        executePlan(pending,BuildRunType.SELECTION,plan.command(),plan.javaHome(),skipped,workspaceOf(pending));
        return new ContinuationResult("accepted","Remaining build started.");
    }
    public void cancelContinuations() {
        canceledRuns.addAll(activeRuns);
        activeRuns.forEach(continuations::cancelRun); continuations.cancelAll();
    }

    /**
     * Builds all projects in a workspace sequentially.
     * 
     * @param workspaceId The workspace ID.
     */
    public void buildWorkspaceSequentially(final Long workspaceId) {
        final List<MavenProject> projects = workspaceService.getProjectsForWorkspace(workspaceId, true);
        buildProjectsSequentially(projects, BuildRunType.FULL);
    }

    /**
     * Builds a contiguous, inclusive selection of a workspace's execution order.
     *
     * @param workspaceId workspace containing the project
     * @param projectId selected project identifier
     * @param mode selection direction
     */
    public void buildWorkspaceRange(final Long workspaceId, final Long projectId, final BuildRangeMode mode) {
        final List<MavenProject> projects = workspaceService.getProjectsForWorkspace(workspaceId, true);
        final BuildRunType type = mode == BuildRangeMode.ONLY ? BuildRunType.PROJECT : BuildRunType.RANGE;
        buildProjectsSequentially(selectBuildRange(projects, projectId, mode), type);
    }

    /**
     * Selects an inclusive build range from projects already sorted in execution order.
     *
     * @param projects projects in execution order
     * @param projectId selected project identifier
     * @param mode selection direction
     * @return selected projects in execution order
     */
    List<MavenProject> selectBuildRange(final List<MavenProject> projects, final Long projectId,
            final BuildRangeMode mode) {
        final int selectedIndex = findProjectIndex(projects, projectId);
        if (selectedIndex >= 0 && !projects.get(selectedIndex).isMaven()) throw new IllegalArgumentException("This operation requires a Maven project.");
        if (selectedIndex < 0) {
            throw new IllegalArgumentException("The selected project does not belong to this workspace.");
        }

        return switch (mode) {
            case ONLY -> List.of(projects.get(selectedIndex));
            case FROM -> List.copyOf(projects.subList(selectedIndex, projects.size()));
            case THROUGH -> List.copyOf(projects.subList(0, selectedIndex + 1));
        };
    }

    /**
     * Finds a project index by persistent identifier.
     *
     * @param projects projects to search
     * @param projectId project identifier
     * @return matching index or -1 when absent
     */
    private int findProjectIndex(final List<MavenProject> projects, final Long projectId) {
        for (int index = 0; index < projects.size(); index++) {
            if (projectId.equals(projects.get(index).getId())) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Performs a 'git fetch' on a project.
     * 
     * @param project The project to fetch.
     */
    public void gitFetch(final MavenProject project) {
        final File projectDir = getProjectDir(project);
        processExecutionService.executeCommand(project.getDisplayName(), projectDir, "git", "fetch")
                .exceptionally(error -> new CommandResult(-1, List.of()))
                .thenAccept(result -> {
                    final ActionSummary summary = ActionSummary.builder()
                            .withAction("git-fetch")
                            .withSuccess(result.getExitCode() == 0)
                            .withFailedProjects(result.getExitCode() == 0 ? List.of() : List.of(project.getDisplayName()))
                            .build();
                    publishSummary(summary, List.of(project));
                });
    }

    /**
     * Performs a 'git pull' on a project.
     * 
     * @param project The project to pull.
     */
    public void gitPull(final MavenProject project) {
        final File projectDir = getProjectDir(project);
        processExecutionService.executeCommand(project.getDisplayName(), projectDir, "git", "pull")
                .exceptionally(error -> new CommandResult(-1, List.of()))
                .thenAccept(result -> {
                    boolean hasChanges = true;
                    for (final String line : result.getOutput()) {
                        if (line.contains("Already up to date") || line.contains("Ya al día") || line.contains("up-to-date")) {
                            hasChanges = false;
                            break;
                        }
                    }
                    final List<String> changed = new java.util.ArrayList<>();
                    final List<String> noChanges = new java.util.ArrayList<>();
                    if (result.getExitCode() == 0) {
                        if (hasChanges) changed.add(project.getDisplayName());
                        else noChanges.add(project.getDisplayName());
                    }
                    final ActionSummary summary = ActionSummary.builder()
                            .withAction("git-pull")
                            .withSuccess(result.getExitCode() == 0)
                            .withFailedProjects(result.getExitCode() == 0 ? List.of() : List.of(project.getDisplayName()))
                            .withChangedProjects(changed)
                            .withNoChangesProjects(noChanges)
                            .build();
                    publishSummary(summary, List.of(project));
                });
    }

    /**
     * Performs a bulk 'git fetch' on multiple projects.
     * 
     * @param projects The list of projects.
     */
    public void bulkGitFetch(final List<MavenProject> projects) {
        final List<MavenProject> enabledProjects = new java.util.ArrayList<>();
        final List<CompletableFuture<CommandResult>> futures = new java.util.ArrayList<>();
        for (final MavenProject project : projects) {
            if (!project.isEnabled()) continue;
            enabledProjects.add(project);
            final File projectDir = getProjectDir(project);
            futures.add(processExecutionService.executeCommand(project.getDisplayName(), projectDir, "git", "fetch")
                    .exceptionally(error -> new CommandResult(-1, List.of())));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                final List<String> failed = new java.util.ArrayList<>();
                for (int i = 0; i < futures.size(); i++) {
                    if (futures.get(i).join().getExitCode() != 0) {
                        failed.add(enabledProjects.get(i).getDisplayName());
                    }
                }
                final ActionSummary summary = ActionSummary.builder()
                        .withAction("git-fetch")
                        .withSuccess(failed.isEmpty())
                        .withFailedProjects(failed)
                        .build();
                publishSummary(summary, projects);
            });
    }

    /**
     * Performs a bulk 'git pull' on multiple projects.
     * 
     * @param projects The list of projects.
     */
    public void bulkGitPull(final List<MavenProject> projects) {
        final List<MavenProject> enabledProjects = new java.util.ArrayList<>();
        final List<CompletableFuture<CommandResult>> futures = new java.util.ArrayList<>();

        for (final MavenProject project : projects) {
            if (!project.isEnabled()) continue;
            enabledProjects.add(project);
            final File projectDir = getProjectDir(project);
            futures.add(processExecutionService.executeCommand(project.getDisplayName(), projectDir, "git", "pull")
                    .exceptionally(error -> new CommandResult(-1, List.of())));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                final List<String> changed = new java.util.ArrayList<>();
                final List<String> noChanges = new java.util.ArrayList<>();
                final List<String> failed = new java.util.ArrayList<>();
                for (int i = 0; i < futures.size(); i++) {
                    final CommandResult result = futures.get(i).join();
                    final String artifactId = enabledProjects.get(i).getDisplayName();
                    if (result.getExitCode() != 0) {
                        failed.add(artifactId);
                    } else if (result.getOutput().stream().anyMatch(line ->
                            line.contains("Already up to date") || line.contains("Ya al día") || line.contains("up-to-date"))) {
                        noChanges.add(artifactId);
                    } else {
                        changed.add(artifactId);
                    }
                }
                final ActionSummary summary = ActionSummary.builder()
                        .withAction("git-pull")
                        .withSuccess(failed.isEmpty())
                        .withFailedProjects(failed)
                        .withChangedProjects(changed)
                        .withNoChangesProjects(noChanges)
                        .build();
                publishSummary(summary, projects);
            });
    }

    /**
     * Performs a 'git checkout -- .' on a project to discard local unstaged changes.
     * 
     * @param project The project.
     */
    public void gitDiscard(final MavenProject project) {
        final File projectDir = getProjectDir(project);
        processExecutionService.executeCommand(project.getDisplayName(), projectDir, "git", "checkout", "--", ".")
                .thenAccept(result -> {
                    final ActionSummary summary = ActionSummary.builder()
                            .withAction("git-discard")
                            .withSuccess(result.getExitCode() == 0)
                            .build();
                    publishSummary(summary, List.of(project));
                });
    }

    /**
     * Performs a 'git restore --staged .' on a project to unstage staged changes.
     * 
     * @param project The project.
     */
    public void gitUnstage(final MavenProject project) {
        final File projectDir = getProjectDir(project);
        processExecutionService.executeCommand(project.getDisplayName(), projectDir, "git", "restore", "--staged", ".")
                .thenAccept(result -> {
                    final ActionSummary summary = ActionSummary.builder()
                            .withAction("git-unstage")
                            .withSuccess(result.getExitCode() == 0)
                            .build();
                    publishSummary(summary, List.of(project));
                });
    }

    /**
     * Performs a bulk 'git checkout -- .' on multiple projects.
     * 
     * @param projects The list of projects.
     */
    public void bulkGitDiscard(final List<MavenProject> projects) {
        final List<CompletableFuture<CommandResult>> futures = new java.util.ArrayList<>();
        for (final MavenProject project : projects) {
            if (!project.isEnabled()) continue;
            final File projectDir = getProjectDir(project);
            futures.add(processExecutionService.executeCommand(project.getDisplayName(), projectDir, "git", "checkout", "--", "."));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                final ActionSummary summary = ActionSummary.builder()
                        .withAction("git-discard")
                        .withSuccess(true)
                        .build();
                publishSummary(summary, projects);
            });
    }

    /**
     * Performs a bulk 'git restore --staged .' on multiple projects.
     * 
     * @param projects The list of projects.
     */
    public void bulkGitUnstage(final List<MavenProject> projects) {
        final List<CompletableFuture<CommandResult>> futures = new java.util.ArrayList<>();
        for (final MavenProject project : projects) {
            if (!project.isEnabled()) continue;
            final File projectDir = getProjectDir(project);
            futures.add(processExecutionService.executeCommand(project.getDisplayName(), projectDir, "git", "restore", "--staged", "."));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                final ActionSummary summary = ActionSummary.builder()
                        .withAction("git-unstage")
                        .withSuccess(true)
                        .build();
                publishSummary(summary, projects);
            });
    }

    /**
     * Performs a bulk 'git stash push' on multiple projects, optionally including
     * untracked files. Per-project failures are reported in the action summary.
     * 
     * @param projects         The list of projects.
     * @param includeUntracked Whether untracked files should be stashed as well.
     */
    public void bulkGitStash(final List<MavenProject> projects, final boolean includeUntracked) {
        final List<MavenProject> enabledProjects = new java.util.ArrayList<>();
        final List<CompletableFuture<CommandResult>> futures = new java.util.ArrayList<>();
        for (final MavenProject project : projects) {
            if (!project.isEnabled()) continue;
            final File projectDir = getProjectDir(project);
            enabledProjects.add(project);
            if (includeUntracked) {
                futures.add(processExecutionService.executeCommand(project.getDisplayName(), projectDir, "git", "stash", "push", "-u"));
            } else {
                futures.add(processExecutionService.executeCommand(project.getDisplayName(), projectDir, "git", "stash", "push"));
            }
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                final List<String> failed = new java.util.ArrayList<>();
                for (int i = 0; i < futures.size(); i++) {
                    if (futures.get(i).join().getExitCode() != 0) {
                        failed.add(enabledProjects.get(i).getDisplayName());
                    }
                }
                final ActionSummary summary = ActionSummary.builder()
                        .withAction("git-stash")
                        .withSuccess(failed.isEmpty())
                        .withFailedProjects(failed)
                        .build();
                publishSummary(summary, projects);
            });
    }

    /**
     * Calculates the absolute file system directory for a project.
     * 
     * @param project The Maven project.
     * @return The directory File object.
     */
    private File getProjectDir(final MavenProject project) {
        if (project.getAbsolutePath() != null) {
            return new File(project.getAbsolutePath());
        }
        return new File(new File(project.getWorkspace().getBasePath()), project.getRelativePath());
    }

    /**
     * Resolves the appropriate Java Home path for a workspace.
     * 
     * @param workspace The workspace to check.
     * @return The path to JAVA_HOME, or null if none is configured.
     */
    private String getJavaHomeForWorkspace(final net.olaba.mvnbuilder.entities.Workspace workspace) {
        if (workspace != null && workspace.getJavaInstallation() != null) {
            return workspace.getJavaInstallation().getJavaHome();
        }
        return systemSettingService.getDefaultJavaInstallation()
                .map(net.olaba.mvnbuilder.entities.JavaInstallation::getJavaHome)
                .orElse(null);
    }
}
