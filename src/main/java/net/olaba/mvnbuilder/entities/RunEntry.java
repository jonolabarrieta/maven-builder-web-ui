package net.olaba.mvnbuilder.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A command the user can start from a workspace (a service, a dev server, a tool).
 * The project link is a plain identifier so that refreshes or removals never break
 * the entry; it is resolved against the workspace composition when used.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "name"}))
@Getter @Setter @NoArgsConstructor
public class RunEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @Column(nullable = false) private String name;
    @Column(nullable = false) private int position;
    /** Linked own or imported project; null when unlinked. */
    @Column(name = "project_id") private Long projectId;
    @Column(nullable = false, length = 2000) private String workingDirectory;
    @Column(nullable = false, length = 4000) private String command;
    @Column(nullable = false) private boolean useWorkspaceJava;
    /** Whether the entry was part of the last launch selection. */
    @Column(nullable = false) private boolean lastSelected;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "run_entry_env", joinColumns = @JoinColumn(name = "run_entry_id"))
    @OrderColumn(name = "env_position")
    private List<RunEnvVar> env = new ArrayList<>();
}
