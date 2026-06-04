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
        return CompletableFuture.supplyAsync(() -> {
            final long startTime = System.currentTimeMillis();
            Process process = null;
            final java.util.List<String> output = new java.util.ArrayList<>();
            try {
                // Initialize process builder with the command and directory
                final ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(directory);
                pb.redirectErrorStream(true); // Merge stdout and stderr

                // Inject custom JAVA_HOME and prepand its bin folder to PATH
                if (javaHome != null && !javaHome.trim().isEmpty()) {
                    final File jHomeDir = new File(javaHome.trim());
                    if (jHomeDir.exists() && jHomeDir.isDirectory()) {
                        pb.environment().put("JAVA_HOME", jHomeDir.getAbsolutePath());
                        final String pathSeparator = File.pathSeparator;
                        final String fileSeparator = File.separator;
                        final String binPath = jHomeDir.getAbsolutePath() + fileSeparator + "bin";
                        
                        final String currentPath = pb.environment().get("PATH");
                        if (currentPath != null) {
                            pb.environment().put("PATH", binPath + pathSeparator + currentPath);
                        } else {
                            pb.environment().put("PATH", binPath);
                        }
                        sendLog(label, "Using JAVA_HOME override: " + jHomeDir.getAbsolutePath());
                    } else {
                        sendLog(label, "WARNING: Configured JAVA_HOME path does not exist: " + javaHome + ". Using system default Java.");
                    }
                }

                process = pb.start();
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
                final String durationStr = String.format("%.2f", duration / 1000.0);

                sendLog(label, "--- Finished with exit code: " + exitCode + " (Duration: " + durationStr + "s) ---");
                return new CommandResult(exitCode, output);
            } catch (Exception e) {
                log.error("Error executing command: {}", e.getMessage(), e);
                sendLog(label, "ERROR: " + e.getMessage());
                return new CommandResult(-1, output);
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
        });
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
            final long pid = process.pid();
            log.info("Killing process with PID: {}", pid);

            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                try {
                    // On Windows, destroying the parent batch process (mvn.cmd) often leaves
                    // the child Java process running. 'taskkill /F /T /PID' kills the entire tree.
                    new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid)).start().waitFor();
                } catch (final Exception e) {
                    log.error("Failed to execute taskkill: {}", e.getMessage());
                    process.destroyForcibly();
                }
            } else {
                process.destroy(); // Send SIGTERM
                try {
                    // Wait up to 2 seconds for clean exit before forcing
                    if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                        log.warn("Process did not exit in time, forcing kill...");
                        process.destroyForcibly(); // Send SIGKILL
                    }
                } catch (final InterruptedException e) {
                    process.destroyForcibly();
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
