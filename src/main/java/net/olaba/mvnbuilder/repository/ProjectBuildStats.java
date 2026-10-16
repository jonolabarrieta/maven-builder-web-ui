package net.olaba.mvnbuilder.repository;

/**
 * Aggregated per-project build statistics for a workspace.
 */
public interface ProjectBuildStats {
    /** Artifact ID of the project. */
    String getArtifactId();

    /** Average duration in milliseconds across all recorded runs. */
    Double getAverageMs();

    /** Number of recorded runs that built this project. */
    long getRuns();

    /** Shortest recorded duration in milliseconds. */
    long getMinMs();

    /** Longest recorded duration in milliseconds. */
    long getMaxMs();
}
