package net.olaba.mvnbuilder.service.git;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Shared by the existing project controls and the Git view, including non-Spring callers. */
@org.springframework.stereotype.Component
public final class GitMutationCoordinator {
    private static final ConcurrentHashMap<Path, LockSlot> LOCKS = new ConcurrentHashMap<>();
    private static volatile Semaphore WORKERS = new Semaphore(4, true);
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final class LockSlot { final ReentrantLock lock = new ReentrantLock(true); int users; }
    public GitMutationCoordinator() { }
    @org.springframework.beans.factory.annotation.Value("${workspace.git.mutation-workers:4}")
    public void configure(int count) { WORKERS = new Semaphore(Math.max(1, count), true); }
    public static <T> T mutate(File directory, Supplier<T> operation) {
        Path key = commonDirectory(directory);
        LockSlot slot = LOCKS.compute(key, (ignored, value) -> { if (value == null) value = new LockSlot(); value.users++; return value; });
        ReentrantLock lock = slot.lock;
        Semaphore workers = WORKERS;
        lock.lock();
        boolean permit = false;
        try {
            if (DEPTH.get() == 0) { workers.acquire(); permit = true; }
            DEPTH.set(DEPTH.get() + 1);
            try { return operation.get(); } finally { DEPTH.set(DEPTH.get() - 1); }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new GitProblem(503, "Git operation interrupted.");
        } finally { if (permit) workers.release(); lock.unlock();
            LOCKS.computeIfPresent(key, (ignored, value) -> { value.users--; return value.users == 0 ? null : value; }); }
    }
    public static Path commonDirectory(File directory) {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "--path-format=absolute", "--git-common-dir")
                    .directory(directory).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException(); }
            String path = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).replaceFirst("\\n$", "");
            if (process.exitValue() == 0) return Path.of(path).toRealPath();
        } catch (Exception ignored) { /* Existing error reporting owns invalid project directories. */ }
        return directory.toPath().toAbsolutePath().normalize();
    }
}
