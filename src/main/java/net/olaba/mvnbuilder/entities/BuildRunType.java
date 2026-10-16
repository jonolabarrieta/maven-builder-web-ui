package net.olaba.mvnbuilder.entities;

/**
 * How a recorded build run was requested.
 */
public enum BuildRunType {
    /** Build All: every enabled project of the workspace. */
    FULL,
    /** A contiguous range selected from a module (from/through). */
    RANGE,
    /** A single project (individual build or a range limited to one module). */
    PROJECT,
    /** An arbitrary selection of projects built together (bulk "Build selected"). */
    SELECTION
}
