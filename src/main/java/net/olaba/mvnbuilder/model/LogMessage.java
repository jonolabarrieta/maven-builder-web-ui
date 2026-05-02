package net.olaba.mvnbuilder.model;

/**
 * Log message DTO for streaming process output.
 */
public record LogMessage(
    /** A descriptive label for the logs (e.g., project name). */
    String label, 
    /** The log message content. */
    String content
) {}
