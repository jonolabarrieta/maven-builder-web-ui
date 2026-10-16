package net.olaba.mvnbuilder.model;

/**
 * Request payload for a client-originated local notification (success or error
 * message raised while starting an action).
 */
public record LocalNotificationRequest(
        /** "error" for failures, anything else is treated as success. */
        String severity,
        /** Optional headline; a default is derived when blank. */
        String title,
        /** Required detail text. */
        String message,
        /** Workspace the message belongs to, when known. */
        Long workspaceId,
        /** Workspace name for display, when known. */
        String workspaceName) {
}
