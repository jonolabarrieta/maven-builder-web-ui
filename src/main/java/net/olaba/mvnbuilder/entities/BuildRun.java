package net.olaba.mvnbuilder.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A single build execution, with its wall-clock total and one timing entry per
 * project that was actually built.
 */
@Entity
@Getter
@Setter
@Builder(setterPrefix = "with")
@NoArgsConstructor
@AllArgsConstructor
public class BuildRun {

    /** Unique identifier for the build run. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Workspace the run belongs to. */
    private Long workspaceId;

    /** Workspace name for display. */
    private String workspaceName;

    /** How the run was requested. */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private BuildRunType type;

    /** When the run started. */
    private Instant startedAt;

    /** When the run finished (successfully or with a failure). */
    private Instant finishedAt;

    /** Wall-clock duration of the whole run in milliseconds. */
    private long totalDurationMs;

    /** Whether every built project succeeded. */
    @Column(name = "is_success", nullable = false)
    private boolean success;

    /** Number of projects that were actually built. */
    private int builtProjectCount;

    /** Per-project timings, in execution order. */
    @OneToMany(mappedBy = "buildRun", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("executionIndex ASC")
    @Builder.Default
    private List<BuildRunProject> projects = new ArrayList<>();
}
