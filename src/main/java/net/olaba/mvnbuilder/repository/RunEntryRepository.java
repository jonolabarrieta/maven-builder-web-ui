package net.olaba.mvnbuilder.repository;

import net.olaba.mvnbuilder.entities.RunEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RunEntryRepository extends JpaRepository<RunEntry, Long> {
    List<RunEntry> findByWorkspaceIdOrderByPositionAscIdAsc(Long workspaceId);
}
