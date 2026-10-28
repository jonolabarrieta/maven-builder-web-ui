package net.olaba.mvnbuilder.service;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Process helpers shared by builds and RUN entries: whole-tree termination and
 * JAVA_HOME injection.
 */
@Slf4j
public final class ProcessTrees {
    /** Grace period given to a process tree before it is killed forcibly. */
    public static final Duration DEFAULT_GRACE = Duration.ofSeconds(5);

    private ProcessTrees() { }

    /** @return true when running on Windows */
    public static boolean windows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * Terminates a process and every descendant. Descendants are collected before
     * signalling because they re-parent once the parent dies.
     *
     * @param process the root process
     * @param grace time allowed for a graceful exit before forcing
     */
    public static void terminate(final Process process, final Duration grace) {
        if (process == null) return;
        if (windows()) {
            if (!process.isAlive()) return;
            try {
                // taskkill /T already walks the whole tree on Windows.
                new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(process.pid())).start().waitFor(grace.toMillis(), TimeUnit.MILLISECONDS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final Exception e) {
                log.error("Failed to execute taskkill: {}", e.getMessage());
            }
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            return;
        }
        final List<ProcessHandle> tree = new java.util.ArrayList<>(process.descendants().toList());
        tree.add(process.toHandle());
        tree.forEach(ProcessHandle::destroy);
        final long deadline = System.nanoTime() + grace.toNanos();
        for (final ProcessHandle handle : tree) {
            final long remaining = deadline - System.nanoTime();
            if (remaining <= 0) break;
            try {
                handle.onExit().get(remaining, TimeUnit.NANOSECONDS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (final Exception timeout) {
                break;
            }
        }
        boolean forced = false;
        for (final ProcessHandle handle : tree) {
            if (handle.isAlive()) { handle.destroyForcibly(); forced = true; }
        }
        if (forced) log.warn("Process tree of PID {} did not exit in time and was killed forcibly.", process.pid());
        try {
            process.waitFor(grace.toMillis(), TimeUnit.MILLISECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sets JAVA_HOME and places its bin directory first on PATH.
     *
     * @param environment process environment to update
     * @param javaHome configured JAVA_HOME (nullable)
     * @return null when applied or not configured, otherwise a warning message
     */
    public static String applyJavaHome(final Map<String, String> environment, final String javaHome) {
        if (javaHome == null || javaHome.trim().isEmpty()) return null;
        final File jHomeDir = new File(javaHome.trim());
        if (!jHomeDir.exists() || !jHomeDir.isDirectory()) {
            return "WARNING: Configured JAVA_HOME path does not exist: " + javaHome + ". Using system default Java.";
        }
        environment.put("JAVA_HOME", jHomeDir.getAbsolutePath());
        final String binPath = jHomeDir.getAbsolutePath() + File.separator + "bin";
        final String pathKey = environment.keySet().stream().filter(k -> k.equalsIgnoreCase("PATH")).findFirst().orElse("PATH");
        final String currentPath = environment.get(pathKey);
        environment.put(pathKey, currentPath == null ? binPath : binPath + File.pathSeparator + currentPath);
        return null;
    }
}
