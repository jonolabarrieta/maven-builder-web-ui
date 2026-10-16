package net.olaba.mvnbuilder.repository;

import net.olaba.mvnbuilder.entities.BuildRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for recorded build runs.
 */
public interface BuildRunRepository extends JpaRepository<BuildRun, Long> {

    /**
     * Returns a page of build runs for a workspace, newest first.
     *
     * @param workspaceId workspace identifier
     * @param pageable    paging request
     * @return page of runs
     */
    Page<BuildRun> findByWorkspaceIdOrderByStartedAtDescIdDesc(Long workspaceId, Pageable pageable);

    /**
     * Counts the recorded runs of a workspace.
     *
     * @param workspaceId workspace identifier
     * @return number of runs
     */
    long countByWorkspaceId(Long workspaceId);
}
