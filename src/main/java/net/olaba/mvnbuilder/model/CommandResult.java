package net.olaba.mvnbuilder.model;

import lombok.Getter;

import java.util.List;

/**
 * Represents the output and exit code of a completed process execution.
 */
@Getter
public class CommandResult {
    /** The process exit code. */
    private final int exitCode;

    /** The captured standard and error output lines. */
    private final List<String> output;

    /** The measured execution time in milliseconds. */
    private final long durationMs;

    /**
     * Creates a result without a measured duration.
     *
     * @param exitCode the process exit code
     * @param output   the captured output lines
     */
    public CommandResult(final int exitCode, final List<String> output) {
        this(exitCode, output, 0L);
    }

    /**
     * Creates a result with a measured duration.
     *
     * @param exitCode   the process exit code
     * @param output     the captured output lines
     * @param durationMs the measured execution time in milliseconds
     */
    public CommandResult(final int exitCode, final List<String> output, final long durationMs) {
        this.exitCode = exitCode;
        this.output = output;
        this.durationMs = durationMs;
    }
}
