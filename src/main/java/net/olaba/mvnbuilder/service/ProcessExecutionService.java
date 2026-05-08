package net.olaba.mvnbuilder.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.olaba.mvnbuilder.model.LogMessage;
import net.olaba.mvnbuilder.model.ProcessInfo;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for executing external processes (like Maven commands)
 * and streaming their output to the frontend via WebSockets.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProcessExecutionService {

    private final SimpMessagingTemplate messagingTemplate;
    private Process currentProcess;

    /**
     * Executes a command asynchronously and returns a CompletableFuture with the exit code.
     * 
     * @param label A descriptive label for the logs (e.g., project name).
     * @param directory The working directory where the command should be executed.
     * @param command The command and its arguments.
     * @return A CompletableFuture that completes with the process exit code.
     */
    @Async
    public CompletableFuture<Integer> executeCommand(final String label, final File directory, final String... command) {
        return CompletableFuture.supplyAsync(() -> {
            final long startTime = System.currentTimeMillis();
            try {
                // Initialize process builder with the command and directory
                final ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(directory);
                pb.redirectErrorStream(true); // Merge stdout and stderr

                this.currentProcess = pb.start();
                final long pid = currentProcess.pid();

                sendLog(label, "--- Starting: " + String.join(" ", command) + " (PID: " + pid + ") ---");
                // Notify UI about the running PID
                sendProcessInfo(pid, true);

                // Stream the process output in real-time
                try (InputStream is = currentProcess.getInputStream();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // Strip ANSI escape codes to ensure clean text logs in the UI
                        final String cleanLine = line.replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");
                        sendLog(label, cleanLine);
                    }
                }

                // Wait for process completion and calculate metrics
                final int exitCode = currentProcess.waitFor();
                final long duration = System.currentTimeMillis() - startTime;
                final String durationStr = String.format("%.2f", duration / 1000.0);

                sendLog(label, "--- Finished with exit code: " + exitCode + " (Duration: " + durationStr + "s) ---");
                sendProcessInfo(0, false); // Clear PID on UI
                return exitCode;
            } catch (Exception e) {
                log.error("Error executing command: {}", e.getMessage(), e);
                sendLog(label, "ERROR: " + e.getMessage());
                sendProcessInfo(0, false);
                return -1;
            } finally {
                this.currentProcess = null;
            }
        });
    }

    /**
     * Attempts to terminate the currently running process.
     * It tries a normal termination first, followed by a forced kill if necessary.
     */
    public void killCurrentProcess() {
        if (currentProcess != null && currentProcess.isAlive()) {
            final long pid = currentProcess.pid();
            log.info("Killing process with PID: {}", pid);

            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                try {
                    // On Windows, destroying the parent batch process (mvn.cmd) often leaves
                    // the child Java process running. 'taskkill /F /T /PID' kills the entire tree.
                    new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid)).start().waitFor();
                } catch (final Exception e) {
                    log.error("Failed to execute taskkill: {}", e.getMessage());
                    currentProcess.destroyForcibly();
                }
            } else {
                currentProcess.destroy(); // Send SIGTERM
                try {
                    // Wait up to 2 seconds for clean exit before forcing
                    if (!currentProcess.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                        log.warn("Process did not exit in time, forcing kill...");
                        currentProcess.destroyForcibly(); // Send SIGKILL
                    }
                } catch (final InterruptedException e) {
                    currentProcess.destroyForcibly();
                    Thread.currentThread().interrupt();
                }
            }
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
