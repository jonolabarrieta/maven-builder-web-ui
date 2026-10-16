package net.olaba.mvnbuilder.entities;

/**
 * Source category of a persisted notification.
 */
public enum NotificationCategory {
    /** Build or Git action summary (the content of an action-summary toast). */
    ACTION,
    /** Build failure that would otherwise offer the retry prompt. */
    BUILD_FAILURE,
    /** Client-side success or error message raised while starting an action. */
    LOCAL
}
