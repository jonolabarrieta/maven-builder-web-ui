package net.olaba.mvnbuilder.entities;

import jakarta.persistence.*;
import lombok.*;
import java.util.*;

/** A consumer-owned group; the parent reference never cascades to shared projects. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "parent_workspace_id"}))
@Getter @Setter @NoArgsConstructor
public class WorkspaceProjectGroup {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "workspace_id") private Workspace workspace;
    @ManyToOne @JoinColumn(name = "parent_workspace_id") private Workspace parentWorkspace;
    @Column(nullable = false) private String name;
    @ElementCollection @OrderColumn(name = "member_position")
    @CollectionTable(name = "workspace_group_members", joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "project_id") private List<Long> memberIds = new ArrayList<>();
    @ElementCollection
    @CollectionTable(name = "workspace_group_enabled", joinColumns = @JoinColumn(name = "group_id"))
    @MapKeyColumn(name = "project_id") @Column(name = "enabled")
    private Map<Long, Boolean> enabledOverrides = new HashMap<>();
    public boolean isImported() { return parentWorkspace != null; }
}
