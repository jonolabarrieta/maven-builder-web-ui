package net.olaba.mvnbuilder.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the long-running processes started from RUN entries. It is deliberately
 * separate from {@link ProcessExecutionService}: builds never see, count or kill
 * these processes, and each process is read by its own virtual thread instead of
 * holding a pool thread.
 */
@Service
@Slf4j
public class RunProcessRegistry {
    /** Lines kept per entry for page reloads. */
    public static final int BUFFER_LINES = 2000;
    private static final String ANSI = "\u001B\\[[;\\d]*[ -/]*[@-~]";

    public enum State { RUNNING, STOPPED, CRASHED }

    /** One buffered output line. */
    public record Line(long seq, String content) { }

    /** Message published to {@code /topic/run/{workspaceId}}; {@code type} is "line" or "state". */
    public record RunMessage(String type, Long entryId, String name, long seq, String content,
                             State state, Integer exitCode, Long pid, boolean started) { }

    /** State and buffered output of one entry, used to restore tabs after a reload. */
    public record Snapshot(Long entryId, String name, State state, Integer exitCode, Long pid, long lastSeq, List<Line> lines) { }

    /** Raised when an entry cannot be started; the message names the entry. */
    public static class StartException extends RuntimeException {
        public StartException(String message) { super(message); }
    }

    private static final class Handle {
        final Long workspaceId;
        final Long entryId;
        String name;
        Process process;
        State state = State.STOPPED;
        Integer exitCode;
        boolean stopRequested;
        long generation;
        long nextSeq = 1;
        final ArrayDeque<Line> buffer = new ArrayDeque<>();

        Handle(Long workspaceId, Long entryId) { this.workspaceId = workspaceId; this.entryId = entryId; }
        boolean alive() { return state == State.RUNNING && process != null && process.isAlive(); }
    }

    private final SimpMessagingTemplate messaging;
    private final Map<Long, Map<Long, Handle>> handles = new ConcurrentHashMap<>();
    private final Duration grace;
    private final Thread shutdownHook = Thread.ofPlatform().unstarted(this::terminateAll);

    @org.springframework.beans.factory.annotation.Autowired
    public RunProcessRegistry(final SimpMessagingTemplate messaging) {
        this(messaging, ProcessTrees.DEFAULT_GRACE);
    }

    RunProcessRegistry(final SimpMessagingTemplate messaging, final Duration grace) {
        this.messaging = messaging;
        this.grace = grace;
        // Fallback for exits that bypass the Spring lifecycle (tray exit, System.exit).
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    /**
     * Starts an entry unless it is already running.
     *
     * @return false when the entry was already running
     * @throws StartException when the working directory is missing
     */
    public boolean start(final RunConfigurationService.LaunchSpec spec) {
        final File directory = new File(spec.workingDirectory());
        if (!directory.isDirectory()) {
            throw new StartException("Cannot start \"" + spec.name() + "\": working directory " + spec.workingDirectory() + " does not exist.");
        }
        final Handle handle = handles.computeIfAbsent(spec.workspaceId(), id -> new ConcurrentHashMap<>())
            .computeIfAbsent(spec.entryId(), id -> new Handle(spec.workspaceId(), spec.entryId()));
        final long generation;
        synchronized (handle) {
            if (handle.alive()) return false;
            handle.name = spec.name();
            handle.buffer.clear();
            handle.exitCode = null;
            handle.stopRequested = false;
            generation = ++handle.generation;
            final ProcessBuilder builder = new ProcessBuilder(ProcessTrees.windows()
                ? List.of("cmd.exe", "/c", spec.command()) : List.of("sh", "-c", spec.command()));
            builder.directory(directory);
            builder.redirectErrorStream(true);
            builder.environment().putAll(spec.env());
            final String warning = ProcessTrees.applyJavaHome(builder.environment(), spec.javaHome());
            try {
                handle.process = builder.start();
            } catch (final Exception e) {
                handle.process = null;
                handle.state = State.CRASHED;
                append(handle, "ERROR: " + e.getMessage());
                publishState(handle, false);
                return true;
            }
            handle.state = State.RUNNING;
            publishState(handle, true);
            if (warning != null) append(handle, warning);
            else if (spec.javaHome() != null) append(handle, "Using JAVA_HOME override: " + spec.javaHome());
            append(handle, "--- Starting: " + spec.command() + " (PID: " + handle.process.pid() + ") ---");
        }
        final Process process = handle.process;
        Thread.ofVirtual().name("run-" + spec.entryId()).start(() -> read(handle, process, generation));
        return true;
    }

    private void read(final Handle handle, final Process process, final long generation) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (handle) {
                    if (handle.generation != generation) return;
                    append(handle, line.replaceAll(ANSI, ""));
                }
            }
        } catch (final Exception e) {
            log.debug("RUN output of entry {} closed: {}", handle.entryId, e.getMessage());
        }
        int code;
        try {
            code = process.waitFor();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        synchronized (handle) {
            if (handle.generation != generation) return;
            handle.exitCode = code;
            handle.state = handle.stopRequested ? State.STOPPED : State.CRASHED;
            append(handle, "--- " + (handle.stopRequested ? "Stopped" : "Exited") + " with exit code: " + code + " ---");
            publishState(handle, false);
        }
    }

    /** Stops an entry's whole process tree; a no-op when it is not running. */
    public void stop(final Long workspaceId, final Long entryId) {
        final Handle handle = handle(workspaceId, entryId);
        if (handle == null) return;
        final Process process;
        synchronized (handle) {
            if (!handle.alive()) return;
            handle.stopRequested = true;
            process = handle.process;
            append(handle, "--- Stopping ---");
        }
        ProcessTrees.terminate(process, grace);
        awaitFinalState(handle, process);
    }

    /** Stops the entry if needed and starts it again with its current saved configuration. */
    public void restart(final RunConfigurationService.LaunchSpec spec) {
        stop(spec.workspaceId(), spec.entryId());
        start(spec);
    }

    /** Drops the retained log of a stopped or crashed entry. */
    public void dismiss(final Long workspaceId, final Long entryId) {
        final Map<Long, Handle> entries = handles.get(workspaceId);
        if (entries == null) return;
        final Handle handle = entries.get(entryId);
        if (handle == null) return;
        synchronized (handle) {
            if (handle.alive()) throw new IllegalStateException("Stop \"" + handle.name + "\" before closing its log.");
            entries.remove(entryId);
        }
    }

    /** Adds an informational line to an entry's retained log, if it has one. */
    public void note(final Long workspaceId, final Long entryId, final String message) {
        final Handle handle = handle(workspaceId, entryId);
        if (handle == null) return;
        synchronized (handle) { append(handle, message); }
    }

    /** @return whether the entry has a live process */
    public boolean isRunning(final Long workspaceId, final Long entryId) {
        final Handle handle = handle(workspaceId, entryId);
        if (handle == null) return false;
        synchronized (handle) { return handle.alive(); }
    }

    /** @return identifiers of the workspace's running entries */
    public Set<Long> runningEntries(final Long workspaceId) {
        final Set<Long> running = new LinkedHashSet<>();
        handles.getOrDefault(workspaceId, Map.of()).forEach((id, handle) -> {
            synchronized (handle) { if (handle.alive()) running.add(id); }
        });
        return running;
    }

    /** @return state and buffered lines of every entry that has a process or a retained log */
    public List<Snapshot> snapshot(final Long workspaceId) {
        final List<Snapshot> result = new ArrayList<>();
        handles.getOrDefault(workspaceId, Map.of()).values().forEach(handle -> {
            synchronized (handle) {
                result.add(new Snapshot(handle.entryId, handle.name, handle.alive() ? State.RUNNING : handle.state, handle.exitCode,
                    handle.process == null ? null : handle.process.pid(), handle.nextSeq - 1, List.copyOf(handle.buffer)));
            }
        });
        return result;
    }

    /** Stops every entry of a workspace and forgets their logs. */
    public void stopWorkspace(final Long workspaceId) {
        final Map<Long, Handle> entries = handles.get(workspaceId);
        if (entries == null) return;
        List.copyOf(entries.keySet()).forEach(id -> stop(workspaceId, id));
        handles.remove(workspaceId);
    }

    /** Terminates every RUN process tree; safe to call more than once. */
    @PreDestroy
    public void close() {
        terminateAll();
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (final IllegalStateException alreadyShuttingDown) {
            // The hook is running or has run.
        }
    }

    private void terminateAll() {
        final List<Process> processes = new ArrayList<>();
        handles.values().forEach(entries -> entries.values().forEach(handle -> {
            synchronized (handle) {
                if (handle.alive()) { handle.stopRequested = true; processes.add(handle.process); }
            }
        }));
        processes.parallelStream().forEach(process -> ProcessTrees.terminate(process, grace));
    }

    private void awaitFinalState(final Handle handle, final Process process) {
        final long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            synchronized (handle) {
                if (handle.process != process || handle.state != State.RUNNING) return;
            }
            try {
                Thread.sleep(20);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private Handle handle(final Long workspaceId, final Long entryId) {
        return handles.getOrDefault(workspaceId, Map.of()).get(entryId);
    }

    /** Appends a line to the ring buffer and publishes it. Callers hold the handle lock. */
    private void append(final Handle handle, final String content) {
        final Line line = new Line(handle.nextSeq++, content);
        handle.buffer.addLast(line);
        while (handle.buffer.size() > BUFFER_LINES) handle.buffer.removeFirst();
        send(handle, new RunMessage("line", handle.entryId, handle.name, line.seq(), content, null, null, null, false));
    }

    private void publishState(final Handle handle, final boolean started) {
        send(handle, new RunMessage("state", handle.entryId, handle.name, handle.nextSeq - 1, null, handle.state, handle.exitCode,
            handle.process == null ? null : handle.process.pid(), started));
    }

    private void send(final Handle handle, final RunMessage message) {
        try {
            messaging.convertAndSend("/topic/run/" + handle.workspaceId, message);
        } catch (final Exception e) {
            log.debug("Unable to publish RUN message: {}", e.getMessage());
        }
    }
}
