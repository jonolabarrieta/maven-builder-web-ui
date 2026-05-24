package net.olaba.mvnbuilder.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * Represents the output and exit code of a completed process execution.
 */
@Getter
@AllArgsConstructor
public class CommandResult {
    /** The process exit code. */
    private final int exitCode;
    
    /** The captured standard and error output lines. */
    private final List<String> output;
}
