package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.model.BuildFailure;
import net.olaba.mvnbuilder.model.LogMessage;

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
        processExecutionService.executeCommand(project.getArtifactId(), projectDir, fullCmd);
    }

    /**
     * Builds a list of projects sequentially, skipping disabled ones.
     * 
     * @param projects The list of projects to build.
     */
    public void buildProjectsSequentially(final List<MavenProject> projects) {
        CompletableFuture<Integer> future = CompletableFuture.completedFuture(0);
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
                        .thenApply(resultExitCode -> {
                            if (resultExitCode != 0) {
                                // Notify failure and provide remaining projects for retry
                                final List<Long> remainingIds = projects.subList(currentIndex, projects.size()).stream()
                                        .map(MavenProject::getId)
                                        .collect(java.util.stream.Collectors.toList());

                                messagingTemplate.convertAndSend("/topic/build-failure", new BuildFailure(
                                        project.getArtifactId(),
                                        project.getId(),
                                        remainingIds));
                            }
                            return resultExitCode;
                        });
            });
        }
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
        processExecutionService.executeCommand(project.getArtifactId(), projectDir, "git", "fetch");
    }

    /**
     * Performs a 'git pull' on a project.
     * 
     * @param project The project to pull.
     */
    public void gitPull(final MavenProject project) {
        final File projectDir = getProjectDir(project);
        processExecutionService.executeCommand(project.getArtifactId(), projectDir, "git", "pull");
    }

    /**
     * Performs a bulk 'git fetch' on multiple projects.
     * 
     * @param projects The list of projects.
     */
    public void bulkGitFetch(final List<MavenProject> projects) {
        for (final MavenProject project : projects) {
            gitFetch(project);
        }
    }

    /**
     * Performs a bulk 'git pull' on multiple projects.
     * 
     * @param projects The list of projects.
     */
    public void bulkGitPull(final List<MavenProject> projects) {
        for (final MavenProject project : projects) {
            gitPull(project);
        }
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
