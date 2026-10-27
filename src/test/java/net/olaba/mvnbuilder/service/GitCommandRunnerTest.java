package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.service.git.GitCommandRunner;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.*;

class GitCommandRunnerTest {
    @TempDir Path root;

    @Test void saturatedRemoteCapacityDoesNotDelayLocalReads() throws Exception {
        GitCommandRunner git = new GitCommandRunner();
        git.configureReads(1); git.configureRemotes(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> remote = executor.submit(() -> git.run(root, null, Map.of(), true, "-c", "alias.slow=!sleep 3", "slow"));
            Thread.sleep(300);
            assertFalse(remote.isDone(), "the remote fixture must still hold its permit");
            long started = System.nanoTime();
            assertTrue(git.text(root, "--version").startsWith("git version"));
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2000, "local read waited for remote capacity");
            remote.get(10, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); git.close(); }
    }
}
