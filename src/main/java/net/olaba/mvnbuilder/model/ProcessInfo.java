package net.olaba.mvnbuilder.model;

/**
 * Process metadata DTO containing execution status.
 */
public record ProcessInfo(
    /** The system process ID. */
    long pid, 
    /** Flag indicating if the process is currently active. */
    boolean active
) {}
