package net.olaba.mvnbuilder.repository;

import net.olaba.mvnbuilder.entities.BuildRunProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Repository for the per-project timings of a build run.
 */
public interface BuildRunProjectRepository extends JpaRepository<BuildRunProject, Long> {

    /**
     * Returns the timings for the given runs in execution order.
     *
     * @param buildRunIds run identifiers
     * @return timing entries
     */
    List<BuildRunProject> findByBuildRunIdInOrderByExecutionIndexAsc(Collection<Long> buildRunIds);

    /**
     * Returns per-project build statistics across every run of a workspace.
     *
     * @param workspaceId workspace identifier
     * @return aggregated statistics, slowest first
     */
    @Query("select p.artifactId as artifactId, avg(p.durationMs) as averageMs, count(p) as runs, "
            + "min(p.durationMs) as minMs, max(p.durationMs) as maxMs "
            + "from BuildRunProject p where p.buildRun.workspaceId = :workspaceId "
            + "group by p.artifactId order by avg(p.durationMs) desc")
    List<ProjectBuildStats> projectAverages(@Param("workspaceId") Long workspaceId);
}
