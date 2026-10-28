package net.olaba.mvnbuilder.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ProcessTreesTest {
    @TempDir Path directory;

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void terminateKillsEveryDescendant() throws Exception {
        Process process = new ProcessBuilder("sh", "-c", "sleep 300 & sleep 300 & wait").start();
        List<ProcessHandle> children = waitForDescendants(process, 2);
        assertTrue(children.size() >= 2, "expected background children");

        ProcessTrees.terminate(process, Duration.ofSeconds(2));

        assertFalse(process.isAlive());
        for (ProcessHandle child : children) {
            child.onExit().get(3, TimeUnit.SECONDS);
            assertFalse(child.isAlive(), "descendant " + child.pid() + " survived");
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void terminateForcesProcessesIgnoringSigterm() throws Exception {
        Process process = new ProcessBuilder("sh", "-c", "trap '' TERM; sleep 300 & wait").start();
        waitForDescendants(process, 1);
        long start = System.nanoTime();
        ProcessTrees.terminate(process, Duration.ofMillis(500));
        assertFalse(process.isAlive());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 5);
    }

    @Test
    void terminateIgnoresFinishedOrMissingProcesses() {
        assertDoesNotThrow(() -> ProcessTrees.terminate(null, Duration.ofMillis(10)));
    }

    @Test
    void javaHomeIsInjectedFirstOnPath() {
        Map<String, String> environment = new HashMap<>(Map.of("PATH", "/usr/bin"));
        assertNull(ProcessTrees.applyJavaHome(environment, directory.toString()));
        assertEquals(directory.toFile().getAbsolutePath(), environment.get("JAVA_HOME"));
        assertTrue(environment.get("PATH").startsWith(directory.resolve("bin").toString()));
        assertTrue(environment.get("PATH").endsWith("/usr/bin"));
    }

    @Test
    void missingJavaHomeReturnsWarningAndLeavesEnvironment() {
        Map<String, String> environment = new HashMap<>(Map.of("PATH", "/usr/bin"));
        String warning = ProcessTrees.applyJavaHome(environment, directory.resolve("missing").toString());
        assertNotNull(warning);
        assertTrue(warning.contains("does not exist"));
        assertNull(environment.get("JAVA_HOME"));
        assertEquals("/usr/bin", environment.get("PATH"));
    }

    private static List<ProcessHandle> waitForDescendants(Process process, int count) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            List<ProcessHandle> children = process.descendants().toList();
            if (children.size() >= count) return children;
            Thread.sleep(50);
        }
        return process.descendants().toList();
    }
}
