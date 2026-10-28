package net.olaba.mvnbuilder.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ProcessExecutionKillTest {
    @TempDir Path directory;

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void killingABuildTerminatesItsChildProcesses() throws Exception {
        ProcessExecutionService service = new ProcessExecutionService(mock(SimpMessagingTemplate.class));
        var running = CompletableFuture.supplyAsync(() -> service.executeCommand("build", directory.toFile(), "sh", "-c", "sleep 300 & wait").join());

        ProcessHandle child = null;
        for (int i = 0; i < 100 && child == null; i++) {
            child = ProcessHandle.current().descendants()
                .filter(h -> h.info().command().map(c -> c.endsWith("sleep")).orElse(false)).findFirst().orElse(null);
            if (child == null) Thread.sleep(50);
        }
        assertNotNull(child, "sleep child did not start");

        service.killCurrentProcess();

        running.get(10, TimeUnit.SECONDS);
        child.onExit().get(5, TimeUnit.SECONDS);
        assertFalse(child.isAlive());
    }
}
