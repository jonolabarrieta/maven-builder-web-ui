package net.olaba.mvnbuilder.repository;
import net.olaba.mvnbuilder.entities.WorkspaceProjectGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface WorkspaceProjectGroupRepository extends JpaRepository<WorkspaceProjectGroup, Long> {
    List<WorkspaceProjectGroup> findByWorkspaceIdOrderByIdAsc(Long workspaceId);
    List<WorkspaceProjectGroup> findByParentWorkspaceId(Long parentId);
}
