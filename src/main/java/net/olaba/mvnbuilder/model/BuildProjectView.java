package net.olaba.mvnbuilder.model;

/**
 * A project's timing within a recorded build run, for display.
 */
public record BuildProjectView(String artifactId, long durationMs, boolean success, int executionIndex) {

    /** Human-readable duration. */
    public String getDurationLabel() {
        return DurationFormat.human(durationMs);
    }
}
