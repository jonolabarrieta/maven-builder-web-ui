package net.olaba.mvnbuilder.model;

import java.util.List;

/**
 * DTO for build failure notification.
 */
public record BuildFailure(
    /** The name of the project that failed. */
    String projectName, 
    /** The unique identifier of the project that failed. */
    Long projectId, 
    /** List of project IDs that were remaining in the queue when failure occurred. */
    List<Long> remainingProjectIds
) {}
