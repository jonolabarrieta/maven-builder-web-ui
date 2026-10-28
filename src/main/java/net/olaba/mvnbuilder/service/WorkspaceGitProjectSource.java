package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.service.git.WorkspaceGitProjects;
import org.springframework.stereotype.Component;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

/** Supplies the Git view with the folders of the workspace's own and imported projects. */
@Component
public class WorkspaceGitProjectSource implements WorkspaceGitProjects {
    private final WorkspaceGroupService groups;
    public WorkspaceGitProjectSource(WorkspaceGroupService groups) { this.groups = groups; }
    @Override public List<ProjectFolder> folders(Workspace workspace) {
        return groups.composition(workspace.getId()).all().stream().map(WorkspaceGitProjectSource::folder).toList();
    }
    /** Older rows may lack an absolute path; they resolve against their owning workspace's base folder. */
    static ProjectFolder folder(MavenProject project) {
        Path folder = null;
        try {
            if (project.getAbsolutePath() != null && !project.getAbsolutePath().isBlank()) folder = Path.of(project.getAbsolutePath());
            else if (project.getRelativePath() != null && project.getWorkspace() != null && project.getWorkspace().getBasePath() != null)
                folder = Path.of(project.getWorkspace().getBasePath()).resolve(project.getRelativePath());
        } catch (InvalidPathException ignored) { /* Reported as a project without a usable folder. */ }
        return new ProjectFolder(project.getId(), project.getDisplayName(), folder == null ? null : folder.toAbsolutePath().normalize());
    }
}
