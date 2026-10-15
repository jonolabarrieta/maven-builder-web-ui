package net.olaba.mvnbuilder.model;

import net.olaba.mvnbuilder.entities.MavenProject;

import java.util.List;
import java.util.Set;

/**
 * Project rows and child indicators derived from one workspace project snapshot.
 *
 * @param topLevelProjects projects without a stored physical ancestor, in execution order
 * @param projectIdsWithChildren identifiers of projects with a stored descendant
 */
public record WorkspaceProjectHierarchy(List<MavenProject> topLevelProjects,
        Set<Long> projectIdsWithChildren) {

    /** Keeps the hierarchy snapshot immutable for callers and templates. */
    public WorkspaceProjectHierarchy {
        topLevelProjects = List.copyOf(topLevelProjects);
        projectIdsWithChildren = Set.copyOf(projectIdsWithChildren);
    }
}
