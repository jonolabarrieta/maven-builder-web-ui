package net.olaba.mvnbuilder.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import net.olaba.mvnbuilder.entities.MavenProject;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for MavenProject entity operations.
 */
@Repository
public interface MavenProjectRepository extends JpaRepository<MavenProject, Long> {

    /**
     * Finds all projects belonging to a workspace, ordered by their execution
     * sequence.
     * 
     * @param workspaceId The workspace ID.
     * @return A list of Maven projects.
     */
    List<MavenProject> findByWorkspaceIdOrderByExecutionOrderAsc(final Long workspaceId);

    /**
     * Finds a project by its workspace and relative path.
     * 
     * @param workspaceId  The workspace ID.
     * @param relativePath The relative path from workspace root.
     * @return An Optional containing the project if found.
     */
    Optional<MavenProject> findByWorkspaceIdAndRelativePath(final Long workspaceId, final String relativePath);

    /**
     * Retrieves a list of all distinct group IDs present in the repository.
     * 
     * @return A list of unique group ID strings.
     */
    @Query("SELECT DISTINCT m.groupId FROM MavenProject m WHERE m.groupId IS NOT NULL")
    List<String> findDistinctGroupIds();

    /**
     * Deletes all projects associated with a specific workspace.
     * 
     * @param workspaceId The workspace ID.
     */
    void deleteByWorkspaceId(final Long workspaceId);
}
