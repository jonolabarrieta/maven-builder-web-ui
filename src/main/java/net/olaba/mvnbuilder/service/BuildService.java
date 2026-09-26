package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.MavenProject;
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
        if (!project.isEnabled()) {
            messagingTemplate.convertAndSend("/topic/logs",
                    new LogMessage(project.getArtifactId(), "SKIPPING: Project is disabled."));

            return;
        }
        final File projectDir = getProjectDir(project);
        final String[] args = getActiveProfileCommand();
        final String[] fullCmd = new String[args.length + 1];
        fullCmd[0] = getMvnCommand();
        System.arraycopy(args, 0, fullCmd, 1, args.length);
        final String javaHome = getJavaHomeForWorkspace(project.getWorkspace());
        processExecutionService.executeCommandWithJavaHome(project.getArtifactId(), projectDir, javaHome, fullCmd)
                .thenAccept(result -> {
                    final ActionSummary summary = ActionSummary.builder()
                            .withAction("build")
                            .withSuccess(result.getExitCode() == 0)
                            .withFailedProject(result.getExitCode() == 0 ? null : project.getArtifactId())
                            .withSucceededProjects(result.getExitCode() == 0 ? List.of(project.getArtifactId()) : List.of())
                            .build();
                    messagingTemplate.convertAndSend("/topic/action-summary", summary);
                });
    }

    /**
     * Builds a list of projects sequentially, skipping disabled ones.
     * 
     * @param projects The list of projects to build.
     */
    public void buildProjectsSequentially(final List<MavenProject> projects) {
        CompletableFuture<Integer> future = CompletableFuture.completedFuture(0);
        final List<String> succeeded = new java.util.ArrayList<>();

        for (int i = 0; i < projects.size(); i++) {
            final int currentIndex = i;
            final MavenProject project = projects.get(i);

            future = future.thenCompose(exitCode -> {
                if (exitCode != 0) {
                    return CompletableFuture.completedFuture(exitCode);
                }

                if (!project.isEnabled()) {
                    messagingTemplate.convertAndSend("/topic/logs",
                            new LogMessage(project.getArtifactId(), "SKIPPING: Project is disabled."));

                    return CompletableFuture.completedFuture(0); // Continue to next project
                }

                final File projectDir = getProjectDir(project);
                final String[] args = getActiveProfileCommand();
                final String[] fullCmd = new String[args.length + 1];
                fullCmd[0] = getMvnCommand();
                System.arraycopy(args, 0, fullCmd, 1, args.length);
                final String javaHome = getJavaHomeForWorkspace(project.getWorkspace());

                return processExecutionService.executeCommandWithJavaHome(project.getArtifactId(), projectDir, javaHome, fullCmd)
                        .thenApply(result -> {
                            final int resultExitCode = result.getExitCode();
                            if (resultExitCode != 0) {
                                // Notify failure and provide remaining projects for retry
                                final List<Long> remainingIds = projects.subList(currentIndex, projects.size()).stream()
                                        .map(MavenProject::getId)
                                        .collect(java.util.stream.Collectors.toList());

                                messagingTemplate.convertAndSend("/topic/build-failure", new BuildFailure(
                                        project.getArtifactId(),
                                        project.getId(),
                                        remainingIds));

                                // Send ActionSummary with error
                                final ActionSummary summary = ActionSummary.builder()
                                        .withAction("build")
                                        .withSuccess(false)
                                        .withFailedProject(project.getArtifactId())
                                        .withSucceededProjects(new java.util.ArrayList<>(succeeded))
                                        .build();
                                messagingTemplate.convertAndSend("/topic/action-summary", summary);
                            } else {
                                succeeded.add(project.getArtifactId());
                            }
                            return resultExitCode;
                        });
            });
        }

        future.thenAccept(exitCode -> {
            if (exitCode == 0) {
                // All succeeded!
                final ActionSummary summary = ActionSummary.builder()
                        .withAction("build")
                        .withSuccess(true)
                        .withSucceededProjects(succeeded)
                        .build();
                messagingTemplate.convertAndSend("/topic/action-summary", summary);
            }
        });
    }

    /**
     * Builds all projects in a workspace sequentially.
     * 
     * @param workspaceId The workspace ID.
     */
    public void buildWorkspaceSequentially(final Long workspaceId) {
        final List<MavenProject> projects = workspaceService.getProjectsForWorkspace(workspaceId, true);
        buildProjectsSequentially(projects);
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
        buildProjectsSequentially(selectBuildRange(projects, projectId, mode));
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
        processExecutionService.executeCommand(project.getArtifactId(), projectDir, "git", "fetch")
                .exceptionally(error -> new CommandResult(-1, List.of()))
                .thenAccept(result -> {
                    final ActionSummary summary = ActionSummary.builder()
                            .withAction("git-fetch")
                            .withSuccess(result.getExitCode() == 0)
                            .withFailedProjects(result.getExitCode() == 0 ? List.of() : List.of(project.getArtifactId()))
                            .build();
                    messagingTemplate.convertAndSend("/topic/action-summary", summary);
                });
    }

    /**
     * Performs a 'git pull' on a project.
     * 
     * @param project The project to pull.
     */
    public void gitPull(final MavenProject project) {
        final File projectDir = getProjectDir(project);
        processExecutionService.executeCommand(project.getArtifactId(), projectDir, "git", "pull")
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
                        if (hasChanges) changed.add(project.getArtifactId());
                        else noChanges.add(project.getArtifactId());
                    }
                    final ActionSummary summary = ActionSummary.builder()
                            .withAction("git-pull")
                            .withSuccess(result.getExitCode() == 0)
                            .withFailedProjects(result.getExitCode() == 0 ? List.of() : List.of(project.getArtifactId()))
                            .withChangedProjects(changed)
                            .withNoChangesProjects(noChanges)
                            .build();
                    messagingTemplate.convertAndSend("/topic/action-summary", summary);
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
            futures.add(processExecutionService.executeCommand(project.getArtifactId(), projectDir, "git", "fetch")
                    .exceptionally(error -> new CommandResult(-1, List.of())));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                final List<String> failed = new java.util.ArrayList<>();
                for (int i = 0; i < futures.size(); i++) {
                    if (futures.get(i).join().getExitCode() != 0) {
                        failed.add(enabledProjects.get(i).getArtifactId());
                    }
                }
                final ActionSummary summary = ActionSummary.builder()
                        .withAction("git-fetch")
                        .withSuccess(failed.isEmpty())
                        .withFailedProjects(failed)
                        .build();
                messagingTemplate.convertAndSend("/topic/action-summary", summary);
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
            futures.add(processExecutionService.executeCommand(project.getArtifactId(), projectDir, "git", "pull")
                    .exceptionally(error -> new CommandResult(-1, List.of())));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                final List<String> changed = new java.util.ArrayList<>();
                final List<String> noChanges = new java.util.ArrayList<>();
                final List<String> failed = new java.util.ArrayList<>();
                for (int i = 0; i < futures.size(); i++) {
                    final CommandResult result = futures.get(i).join();
                    final String artifactId = enabledProjects.get(i).getArtifactId();
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
                messagingTemplate.convertAndSend("/topic/action-summary", summary);
            });
    }

    /**
     * Performs a 'git checkout -- .' on a project to discard local unstaged changes.
     * 
     * @param project The project.
     */
    public void gitDiscard(final MavenProject project) {
        final File projectDir = getProjectDir(project);
        processExecutionService.executeCommand(project.getArtifactId(), projectDir, "git", "checkout", "--", ".")
                .thenAccept(result -> {
                    final ActionSummary summary = ActionSummary.builder()
                            .withAction("git-discard")
                            .withSuccess(result.getExitCode() == 0)
                            .build();
                    messagingTemplate.convertAndSend("/topic/action-summary", summary);
                });
    }

    /**
     * Performs a 'git restore --staged .' on a project to unstage staged changes.
     * 
     * @param project The project.
     */
    public void gitUnstage(final MavenProject project) {
        final File projectDir = getProjectDir(project);
        processExecutionService.executeCommand(project.getArtifactId(), projectDir, "git", "restore", "--staged", ".")
                .thenAccept(result -> {
                    final ActionSummary summary = ActionSummary.builder()
                            .withAction("git-unstage")
                            .withSuccess(result.getExitCode() == 0)
                            .build();
                    messagingTemplate.convertAndSend("/topic/action-summary", summary);
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
            futures.add(processExecutionService.executeCommand(project.getArtifactId(), projectDir, "git", "checkout", "--", "."));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                final ActionSummary summary = ActionSummary.builder()
                        .withAction("git-discard")
                        .withSuccess(true)
                        .build();
                messagingTemplate.convertAndSend("/topic/action-summary", summary);
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
            futures.add(processExecutionService.executeCommand(project.getArtifactId(), projectDir, "git", "restore", "--staged", "."));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                final ActionSummary summary = ActionSummary.builder()
                        .withAction("git-unstage")
                        .withSuccess(true)
                        .build();
                messagingTemplate.convertAndSend("/topic/action-summary", summary);
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
