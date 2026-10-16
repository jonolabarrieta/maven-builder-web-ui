package net.olaba.mvnbuilder.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The measured duration of a single project build within a {@link BuildRun}.
 */
@Entity
@Getter
@Setter
@Builder(setterPrefix = "with")
@NoArgsConstructor
@AllArgsConstructor
public class BuildRunProject {

    /** Unique identifier for the timing entry. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning build run. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "build_run_id")
    private BuildRun buildRun;

    /** Artifact ID of the built project. */
    private String artifactId;

    /** Measured execution time in milliseconds. */
    private long durationMs;

    /** Whether this project succeeded. */
    @Column(name = "is_success", nullable = false)
    private boolean success;

    /** Position of the project within the run. */
    private int executionIndex;
}
