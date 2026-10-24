package net.olaba.mvnbuilder.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Entity representing a persisted, user-reviewable notification. Notifications
 * mirror the transient action-summary toasts, build-failure prompts, and local
 * success/error messages so they remain available after the toast is dismissed,
 * the page is reloaded, or the application restarts.
 */
@Entity
@Getter
@Setter
@Builder(setterPrefix = "with")
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    /** Unique identifier for the notification. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Creation timestamp, used for newest-first ordering and retention. */
    private Instant createdAt;

    /** Notification source category. */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private NotificationCategory category;

    /** Action identifier (e.g. build, git-fetch) when the notification came from an action. */
    private String action;

    /** Whether the underlying operation succeeded. */
    @Column(name = "is_success", nullable = false)
    private boolean success;

    /** Short headline rendered in the history list. */
    private String title;

    /** Human-readable detail text. */
    @Column(length = 2000)
    private String message;

    /** Workspace the notification belongs to, when known. */
    private Long workspaceId;

    /** Workspace name for display, when known. */
    private String workspaceName;

    /** Comma-joined artifact IDs of repositories that failed. */
    @Column(length = 4000)
    private String failedProjects;

    /** Comma-joined artifact IDs that completed before a failure. */
    @Column(length = 4000)
    private String succeededProjects;

    /** Comma-joined artifact IDs that received changes. */
    @Column(length = 4000)
    private String changedProjects;

    /** Comma-joined artifact IDs that were already up to date. */
    @Column(length = 4000)
    private String noChangesProjects;

    @Column(length = 4000)
    private String skippedProjects;

    /** Whether the notification has been seen in the history panel. */
    @Column(name = "is_read", nullable = false)
    private boolean read;
}
