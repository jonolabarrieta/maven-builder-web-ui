package net.olaba.mvnbuilder.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.olaba.mvnbuilder.model.LogMessage;
import net.olaba.mvnbuilder.model.ProcessInfo;
import net.olaba.mvnbuilder.model.CommandResult;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for executing external processes (like Maven commands)
 * and streaming their output to the frontend via WebSockets.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProcessExecutionService {

    private final SimpMessagingTemplate messagingTemplate;
    private final Set<Process> activeProcesses = ConcurrentHashMap.newKeySet();

    /**
     * Executes a command asynchronously and returns a CompletableFuture with the exit code.
     *
     * @param label A descriptive label for the logs (e.g., project name).
     * @param directory The working directory where the command should be executed.
     * @param command The command and its arguments.
     * @return A CompletableFuture that completes with the process exit code.
     */
    @Async
    public CompletableFuture<CommandResult> executeCommand(final String label, final File directory, final String... command) {
        return executeCommandWithJavaHome(label, directory, null, command);
    }

    /**
     * Executes a command asynchronously with a custom Java Home environment variable.
     *
     * @param label A descriptive label for the logs.
     * @param directory The working directory.
     * @param javaHome The path to JAVA_HOME (nullable).
     * @param command The command and arguments.
     * @return A CompletableFuture with command results.
     */
    @Async
    public CompletableFuture<CommandResult> executeCommandWithJavaHome(final String label, final File directory, final String javaHome, final String... command) {
        if (command.length > 0 && "git".equals(command[0])) {
            return net.olaba.mvnbuilder.service.git.GitMutationCoordinator.mutate(directory,
                    () -> executeCoordinated(label, directory, javaHome, command));
        }
        return executeCoordinated(label, directory, javaHome, command);
    }

    private CompletableFuture<CommandResult> executeCoordinated(final String label, final File directory, final String javaHome, final String... command) {
        final long startTime = System.currentTimeMillis();
        final long startNanos = System.nanoTime();
        Process process = null;
        final java.util.List<String> output = new java.util.ArrayList<>();
        try {
            // Initialize process builder with the command and directory
            final ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(directory);
            pb.redirectErrorStream(true); // Merge stdout and stderr
            if (command.length > 0 && "git".equals(command[0])) {
                pb.environment().put("GIT_TERMINAL_PROMPT", "0");
                pb.environment().put("GCM_INTERACTIVE", "never");
            }

            // Inject custom JAVA_HOME and prepend its bin folder to PATH
            if (javaHome != null && !javaHome.trim().isEmpty()) {
                final String warning = ProcessTrees.applyJavaHome(pb.environment(), javaHome);
                sendLog(label, warning != null ? warning : "Using JAVA_HOME override: " + new File(javaHome.trim()).getAbsolutePath());
            }

            process = pb.start();
            if (command.length > 0 && "git".equals(command[0])) {
                process.getOutputStream().close();
                final Process runningGit = process;
                Thread.ofVirtual().name("project-git-deadline").start(() -> {
                    try {
                        if (!runningGit.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)) {
                            sendLog(label, "Git timed out. Refresh repository state before trying again.");
                            runningGit.descendants().forEach(ProcessHandle::destroyForcibly);
                            runningGit.destroyForcibly();
                        }
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                });
            }
            activeProcesses.add(process);
            final long pid = process.pid();

            sendLog(label, "--- Starting: " + String.join(" ", command) + " (PID: " + pid + ") ---");
            // Notify UI about the running PID
            sendProcessInfo(pid, true);

            // Stream the process output in real-time
            try (InputStream is = process.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Strip ANSI escape codes to ensure clean text logs in the UI
                    final String cleanLine = line.replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");
                    sendLog(label, cleanLine);
                    output.add(cleanLine);
                }
            }

            // Wait for process completion and calculate metrics
            final int exitCode = process.waitFor();
            final long duration = System.currentTimeMillis() - startTime;
            final long durationMs = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
            final String durationStr = String.format("%.2f", duration / 1000.0);

            sendLog(label, "--- Finished with exit code: " + exitCode + " (Duration: " + durationStr + "s) ---");
            return CompletableFuture.completedFuture(new CommandResult(exitCode, output, durationMs));
        } catch (Exception e) {
            log.error("Error executing command: {}", e.getMessage(), e);
            sendLog(label, "ERROR: " + e.getMessage());
            final long durationMs = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
            return CompletableFuture.completedFuture(new CommandResult(-1, output, durationMs));
        } finally {
            if (process != null) {
                activeProcesses.remove(process);
            }
            // Check if any other process is still running to update the UI
            if (activeProcesses.isEmpty()) {
                sendProcessInfo(0, false); // Clear PID on UI
            } else {
                // Update UI with one of the remaining active PIDs
                final Process remaining = activeProcesses.iterator().next();
                sendProcessInfo(remaining.pid(), true);
            }
        }
    }

    /**
     * Attempts to terminate all currently running processes.
     * It tries a normal termination first, followed by a forced kill if necessary.
     */
    public void killCurrentProcess() {
        if (activeProcesses.isEmpty()) {
            log.info("No active processes to kill.");
            return;
        }
        log.info("Killing {} active process(es)...", activeProcesses.size());
        for (final Process process : activeProcesses) {
            killProcess(process);
        }
    }

    /**
     * Terminates a specific process.
     */
    private void killProcess(final Process process) {
        if (process != null && process.isAlive()) {
            log.info("Killing process tree with PID: {}", process.pid());
            ProcessTrees.terminate(process, java.time.Duration.ofSeconds(2));
        }
    }

    /**
     * Sends a log message to the WebSocket topic.
     *
     * @param label   The source of the log.
     * @param message The log content.
     */
    private void sendLog(final String label, final String message) {
        messagingTemplate.convertAndSend("/topic/logs", new LogMessage(label, message));
    }

    /**
     * Sends process metadata (PID) to the WebSocket topic.
     *
     * @param pid    The process ID.
     * @param active Whether the process is currently active.
     */
    private void sendProcessInfo(final long pid, final boolean active) {
        messagingTemplate.convertAndSend("/topic/process", new ProcessInfo(pid, active));
    }


}
