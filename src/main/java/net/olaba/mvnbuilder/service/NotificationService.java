package net.olaba.mvnbuilder.service;

import lombok.RequiredArgsConstructor;
import net.olaba.mvnbuilder.entities.Notification;
import net.olaba.mvnbuilder.entities.NotificationCategory;
import net.olaba.mvnbuilder.model.ActionSummary;
import net.olaba.mvnbuilder.model.BuildFailure;
import net.olaba.mvnbuilder.model.LocalNotificationRequest;
import net.olaba.mvnbuilder.model.NotificationDto;
import net.olaba.mvnbuilder.repository.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Persists user-facing notifications so they can be reviewed after the transient
 * toast disappears, the page reloads, or the application restarts. It is also the
 * single write path for live delivery: notifications are stored and then broadcast
 * over STOMP so persistence and delivery cannot drift apart.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** Maximum number of notifications retained in the history. */
    public static final int MAX_HISTORY = 200;

    private static final Map<String, String> GIT_TITLES = Map.of(
            "git-fetch", "Git Fetch",
            "git-pull", "Git Pull",
            "git-discard", "Git Discard",
            "git-unstage", "Git Unstage",
            "git-stash", "Git Stash");

    private final NotificationRepository repository;
    private final SimpMessagingTemplate messagingTemplate;

    private final Object retentionLock = new Object();

    /**
     * Persists and broadcasts an action summary.
     *
     * @param summary       the action summary
     * @param workspaceId   owning workspace id, may be null
     * @param workspaceName owning workspace name, may be null
     */
    @Transactional
    public void publish(final ActionSummary summary, final Long workspaceId, final String workspaceName) {
        recordActionSummary(summary, workspaceId, workspaceName);
        messagingTemplate.convertAndSend("/topic/action-summary", summary);
    }

    /**
     * Persists and broadcasts a build-failure notice.
     *
     * @param failure       the build failure
     * @param workspaceId   owning workspace id, may be null
     * @param workspaceName owning workspace name, may be null
     */
    @Transactional
    public void publishBuildFailure(final BuildFailure failure, final Long workspaceId, final String workspaceName) {
        recordBuildFailure(failure, workspaceId, workspaceName);
        messagingTemplate.convertAndSend("/topic/build-failure", failure);
    }

    /**
     * Records an action summary notification.
     *
     * @param summary       the action summary
     * @param workspaceId   owning workspace id, may be null
     * @param workspaceName owning workspace name, may be null
     * @return the persisted notification
     */
    @Transactional
    public Notification recordActionSummary(final ActionSummary summary, final Long workspaceId,
            final String workspaceName) {
        final String action = summary.getAction();
        final boolean isBuild = "build".equals(action);
        final String title = isBuild ? "Build result" : gitTitle(action) + " result";
        final String message = isBuild
                ? (summary.isSuccess() ? "Build completed." : "Build stopped at " + safe(summary.getFailedProject()) + ".")
                : gitTitle(action) + (summary.isSuccess() ? " finished." : " finished with errors.");
        return save(Notification.builder()
                .withCategory(NotificationCategory.ACTION)
                .withAction(action)
                .withSuccess(summary.isSuccess())
                .withTitle(title)
                .withMessage(message)
                .withWorkspaceId(workspaceId)
                .withWorkspaceName(workspaceName)
                .withFailedProjects(joinList(summary.getFailedProjects()))
                .withSucceededProjects(joinList(summary.getSucceededProjects()))
                .withChangedProjects(joinList(summary.getChangedProjects()))
                .withNoChangesProjects(joinList(summary.getNoChangesProjects()))
                .build());
    }

    /**
     * Records a build-failure notification.
     *
     * @param failure       the build failure
     * @param workspaceId   owning workspace id, may be null
     * @param workspaceName owning workspace name, may be null
     * @return the persisted notification
     */
    @Transactional
    public Notification recordBuildFailure(final BuildFailure failure, final Long workspaceId,
            final String workspaceName) {
        final String projectName = failure.projectName();
        return save(Notification.builder()
                .withCategory(NotificationCategory.BUILD_FAILURE)
                .withAction("build")
                .withSuccess(false)
                .withTitle("Build failed")
                .withMessage("Build failed at " + safe(projectName) + ". Check its log before retrying.")
                .withWorkspaceId(workspaceId)
                .withWorkspaceName(workspaceName)
                .withFailedProjects(joinList(projectName == null ? null : List.of(projectName)))
                .build());
    }

    /**
     * Records a client-originated local success or error message.
     *
     * @param request the local notification request
     * @return the persisted notification
     */
    @Transactional
    public Notification recordLocal(final LocalNotificationRequest request) {
        final boolean isError = "error".equalsIgnoreCase(request.severity());
        final String title = request.title() == null || request.title().isBlank()
                ? (isError ? "Action failed" : "Action completed")
                : request.title();
        return save(Notification.builder()
                .withCategory(NotificationCategory.LOCAL)
                .withSuccess(!isError)
                .withTitle(title)
                .withMessage(safe(request.message()))
                .withWorkspaceId(request.workspaceId())
                .withWorkspaceName(request.workspaceName())
                .build());
    }

    /**
     * Returns the most recent notifications, newest first.
     *
     * @return notification DTOs
     */
    @Transactional(readOnly = true)
    public List<NotificationDto> recent() {
        return repository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, MAX_HISTORY))
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Returns the number of unread notifications.
     *
     * @return unread count
     */
    @Transactional(readOnly = true)
    public long unreadCount() {
        return repository.countByReadFalse();
    }

    /** Marks every notification as read. */
    @Transactional
    public void markAllRead() {
        repository.markAllAsRead();
    }

    /** Deletes the entire notification history. */
    @Transactional
    public void clear() {
        repository.deleteAllInBatch();
    }

    private Notification save(final Notification notification) {
        notification.setCreatedAt(Instant.now());
        notification.setRead(false);
        final Notification saved = repository.save(notification);
        synchronized (retentionLock) {
            prune();
        }
        return saved;
    }

    /** Discards the oldest notifications so that no more than {@link #MAX_HISTORY} remain. */
    private void prune() {
        if (repository.count() <= MAX_HISTORY) {
            return;
        }
        final List<Long> keep = repository.findNewestIds(PageRequest.of(0, MAX_HISTORY));
        if (!keep.isEmpty()) {
            repository.deleteNotIn(keep);
        }
    }

    /**
     * Maps a persisted notification to its response payload.
     *
     * @param notification the notification
     * @return the response DTO
     */
    public NotificationDto toDto(final Notification notification) {
        return new NotificationDto(
                notification.getId(),
                notification.getCreatedAt() == null ? null : notification.getCreatedAt().toString(),
                notification.getCategory(),
                notification.getAction(),
                notification.isSuccess(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getWorkspaceId(),
                notification.getWorkspaceName(),
                splitList(notification.getFailedProjects()),
                splitList(notification.getSucceededProjects()),
                splitList(notification.getChangedProjects()),
                splitList(notification.getNoChangesProjects()),
                notification.isRead());
    }

    private static String gitTitle(final String action) {
        return GIT_TITLES.getOrDefault(action, "Git Action");
    }

    private static String joinList(final List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join(",", values);
    }

    private static List<String> splitList(final String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        final List<String> values = new ArrayList<>();
        Arrays.stream(csv.split(",")).map(String::trim).filter(value -> !value.isEmpty()).forEach(values::add);
        return values;
    }

    private static String safe(final String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
