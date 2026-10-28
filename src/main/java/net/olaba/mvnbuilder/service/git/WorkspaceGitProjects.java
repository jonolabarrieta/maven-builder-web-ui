package net.olaba.mvnbuilder.service.git;

import net.olaba.mvnbuilder.entities.Workspace;
import java.nio.file.Path;
import java.util.List;

/** Registered workspace projects whose folders define the Git view's repositories. */
@FunctionalInterface
public interface WorkspaceGitProjects {
    /** {@code folder} is null when neither an absolute path nor a base-relative path is known. */
    record ProjectFolder(Long id, String name, Path folder) { }
    /** Every project of the workspace, enabled or disabled, including those imported from parent workspaces. */
    List<ProjectFolder> folders(Workspace workspace);
}
