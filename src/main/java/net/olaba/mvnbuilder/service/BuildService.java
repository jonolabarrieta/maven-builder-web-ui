package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.model.BuildFailure;
import net.olaba.mvnbuilder.model.LogMessage;
import net.olaba.mvnbuilder.model.CommandResult;
import net.olaba.mvnbuilder.model.ActionSummary;

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
        processExecutionService.executeCommand(project.getArtifactId(), projectDir, fullCmd)
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

                return processExecutionService.executeCommand(project.getArtifactId(), projectDir, fullCmd)
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
     * Performs a 'git fetch' on a project.
     * 
     * @param project The project to fetch.
     */
    public void gitFetch(final MavenProject project) {
        final File projectDir = getProjectDir(project);
        processExecutionService.executeCommand(project.getArtifactId(), projectDir, "git", "fetch")
                .thenAccept(result -> {
                    final ActionSummary summary = ActionSummary.builder()
                            .withAction("git-fetch")
                            .withSuccess(result.getExitCode() == 0)
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
                    if (hasChanges && result.getExitCode() == 0) {
                        changed.add(project.getArtifactId());
                    } else {
                        noChanges.add(project.getArtifactId());
                    }
                    final ActionSummary summary = ActionSummary.builder()
                            .withAction("git-pull")
                            .withSuccess(result.getExitCode() == 0)
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
        final List<CompletableFuture<CommandResult>> futures = new java.util.ArrayList<>();
        for (final MavenProject project : projects) {
            if (!project.isEnabled()) continue;
            final File projectDir = getProjectDir(project);
            futures.add(processExecutionService.executeCommand(project.getArtifactId(), projectDir, "git", "fetch"));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                final ActionSummary summary = ActionSummary.builder()
                        .withAction("git-fetch")
                        .withSuccess(true)
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
        final List<String> changed = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        final List<String> noChanges = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        final List<CompletableFuture<Void>> futures = new java.util.ArrayList<>();
        
        for (final MavenProject project : projects) {
            if (!project.isEnabled()) continue;
            final File projectDir = getProjectDir(project);
            futures.add(
                processExecutionService.executeCommand(project.getArtifactId(), projectDir, "git", "pull")
                    .thenAccept(result -> {
                        boolean hasChanges = true;
                        for (final String line : result.getOutput()) {
                            if (line.contains("Already up to date") || line.contains("Ya al día") || line.contains("up-to-date")) {
                                hasChanges = false;
                                break;
                            }
                        }
                        if (hasChanges && result.getExitCode() == 0) {
                            changed.add(project.getArtifactId());
                        } else {
                            noChanges.add(project.getArtifactId());
                        }
                    })
            );
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                final ActionSummary summary = ActionSummary.builder()
                        .withAction("git-pull")
                        .withSuccess(true)
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
}
