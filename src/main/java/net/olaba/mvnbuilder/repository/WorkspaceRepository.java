package net.olaba.mvnbuilder.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import net.olaba.mvnbuilder.entities.Workspace;

/**
 * Repository interface for Workspace entity operations.
 */
@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
}
