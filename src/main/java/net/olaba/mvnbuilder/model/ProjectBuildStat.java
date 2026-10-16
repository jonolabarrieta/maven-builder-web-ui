package net.olaba.mvnbuilder.model;

/**
 * Aggregated build statistics for a single project, for display.
 */
public record ProjectBuildStat(String artifactId, double averageMs, long runs, long minMs, long maxMs) {

    /** Human-readable average duration. */
    public String getAverageLabel() {
        return DurationFormat.human(Math.round(averageMs));
    }

    /** Human-readable minimum duration. */
    public String getMinLabel() {
        return DurationFormat.human(minMs);
    }

    /** Human-readable maximum duration. */
    public String getMaxLabel() {
        return DurationFormat.human(maxMs);
    }
}
