package net.olaba.mvnbuilder.model;

/**
 * A single project's measured duration within a build run, as captured while the
 * run executes.
 */
public record BuildProjectTiming(String artifactId, long durationMs, boolean success, int executionIndex) {
}
