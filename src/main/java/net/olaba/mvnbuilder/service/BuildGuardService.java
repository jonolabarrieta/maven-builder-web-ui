package net.olaba.mvnbuilder.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.entities.RunEntry;
import net.olaba.mvnbuilder.model.LocalNotificationRequest;
import net.olaba.mvnbuilder.model.LogMessage;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asks before building projects that have running RUN entries, and optionally
 * stops those entries for the build and restarts them after a successful build.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BuildGuardService {
    /** The user's answer to the guard prompt. */
    public enum Mode {
        STOP_RESTART, IGNORE;

        /** @return the mode, or null when the request carries no answer */
        public static Mode parse(final String value) {
            if (value == null || value.isBlank()) return null;
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException invalid) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Unknown runGuard value: " + value);
            }
        }
    }

    /** A running entry affected by a build. */
    public record RunningEntry(Long id, String name) { }

    /** Raised when a build needs the user's decision; rendered as HTTP 409. */
    @Getter
    public static class GuardRequiredException extends RuntimeException {
        private final List<RunningEntry> entries;
        public GuardRequiredException(List<RunningEntry> entries) {
            super(entries.stream().map(RunningEntry::name).reduce((a, b) -> a + ", " + b)
                .map(names -> "Running RUN entries use these projects: " + names + ".").orElse("Running RUN entries use these projects."));
            this.entries = List.copyOf(entries);
        }
    }

    /**
     * Starts a build with the guard when one is available.
     *
     * @param guard the guard, or null when RUN support is not wired (tests)
     */
    public static void build(final BuildGuardService guard, final Long workspaceId, final String workspaceName,
                             final List<MavenProject> targets, final String runGuard, final Runnable start) {
        final Mode mode = Mode.parse(runGuard);
        if (guard == null) { start.run(); return; }
        guard.build(workspaceId, workspaceName, targets, mode, start);
    }

    private record Pending(Long workspaceId, String workspaceName, List<RunningEntry> entries) { }

    private final RunConfigurationService configurations;
    private final RunProcessRegistry processes;
    private final BuildService builds;
    private final NotificationService notifications;
    private final SimpMessagingTemplate messaging;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    /**
     * Running entries linked to any eligible build target, matched by project id or
     * by the linked project's directory.
     */
    public List<RunningEntry> conflicts(final Long workspaceId, final List<MavenProject> targets) {
        final Set<Long> running = processes.runningEntries(workspaceId);
        if (running.isEmpty() || targets == null || targets.isEmpty()) return List.of();
        final Set<Long> targetIds = new HashSet<>();
        final Set<String> targetDirs = new HashSet<>();
        for (MavenProject target : targets) {
            if (target == null || !target.isMaven() || !target.isEnabled()) continue;
            if (target.getId() != null) targetIds.add(target.getId());
            final String dir = RunConfigurationService.directory(target);
            if (dir != null) targetDirs.add(WorkspaceGroupService.canonical(dir));
        }
        final Map<Long, String> projectDirs = new HashMap<>();
        configurations.projectOptions(workspaceId).forEach(o -> projectDirs.put(o.id(), o.directory()));
        final List<RunningEntry> conflicts = new ArrayList<>();
        for (RunEntry entry : configurations.list(workspaceId)) {
            if (!running.contains(entry.getId()) || entry.getProjectId() == null) continue;
            final String dir = projectDirs.get(entry.getProjectId());
            if (targetIds.contains(entry.getProjectId()) || (dir != null && targetDirs.contains(WorkspaceGroupService.canonical(dir))))
                conflicts.add(new RunningEntry(entry.getId(), entry.getName()));
        }
        return conflicts;
    }

    /**
     * Starts a build, applying the guard.
     *
     * @param mode null when the user has not answered yet
     * @throws GuardRequiredException when running entries are affected and no mode was given
     */
    public void build(final Long workspaceId, final String workspaceName, final List<MavenProject> targets, final Mode mode, final Runnable start) {
        final List<RunningEntry> conflicts = workspaceId == null ? List.of() : conflicts(workspaceId, targets);
        if (conflicts.isEmpty() || mode == Mode.IGNORE) { start.run(); return; }
        if (mode == null) throw new GuardRequiredException(conflicts);
        final String token = UUID.randomUUID().toString();
        for (RunningEntry entry : conflicts) {
            processes.stop(workspaceId, entry.id());
            processes.note(workspaceId, entry.id(), "--- Stopped for a build; it restarts when the build succeeds ---");
        }
        pending.put(token, new Pending(workspaceId, workspaceName, conflicts));
        try {
            builds.startGuarded(token, start);
        } catch (RuntimeException failed) {
            // Nothing was built: put the services back the way they were.
            final Pending stopped = pending.remove(token);
            if (stopped != null) restart(stopped);
            throw failed;
        }
    }

    @EventListener
    public void onBuildFinished(final BuildService.BuildFinishedEvent event) {
        if (event.guardToken() == null) return;
        final Pending stopped = pending.remove(event.guardToken());
        if (stopped == null) return;
        if (event.success()) {
            restart(stopped);
            return;
        }
        final String names = String.join(", ", stopped.entries().stream().map(RunningEntry::name).toList());
        final String message = "The build did not succeed, so these RUN entries were not restarted: " + names + ".";
        for (RunningEntry entry : stopped.entries()) processes.note(stopped.workspaceId(), entry.id(), "--- Not restarted: the build did not succeed ---");
        messaging.convertAndSend("/topic/logs", new LogMessage("Build", message));
        notifications.recordLocal(new LocalNotificationRequest("error", "RUN entries not restarted", message, stopped.workspaceId(), stopped.workspaceName()));
    }

    private void restart(final Pending stopped) {
        final List<String> failures = new ArrayList<>();
        for (RunningEntry entry : stopped.entries()) {
            try {
                processes.start(configurations.launchSpec(stopped.workspaceId(), entry.id()));
            } catch (RuntimeException failed) {
                failures.add(failed.getMessage() == null ? entry.name() : failed.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            final String message = "Some RUN entries could not be restarted: " + String.join(" ", failures);
            messaging.convertAndSend("/topic/logs", new LogMessage("Build", message));
            notifications.recordLocal(new LocalNotificationRequest("error", "RUN entries not restarted", message, stopped.workspaceId(), stopped.workspaceName()));
        }
    }
}
