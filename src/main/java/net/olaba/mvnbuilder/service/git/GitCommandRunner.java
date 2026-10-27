package net.olaba.mvnbuilder.service.git;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Exact Git transport. Machine data never passes through the application's log reader. */
@Component
public class GitCommandRunner {
    @Value("${workspace.git.local-timeout-seconds:20}") private int localTimeout = 20;
    @Value("${workspace.git.remote-timeout-seconds:120}") private int remoteTimeout = 120;
    @Value("${workspace.git.output-limit-bytes:8388608}") private int outputLimit = 8388608;
    // Remote commands (network, hooks) can run for minutes; they never consume local read capacity.
    private volatile Semaphore reads = new Semaphore(defaultReads(), true);
    private volatile Semaphore remotes = new Semaphore(4, true);
    @Value("${workspace.git.read-workers:0}") public void configureReads(int count) { reads = new Semaphore(count > 0 ? count : defaultReads(), true); }
    @Value("${workspace.git.remote-workers:4}") public void configureRemotes(int count) { remotes = new Semaphore(Math.max(1, count), true); }
    private static int defaultReads() { return Math.max(8, Runtime.getRuntime().availableProcessors() * 2); }
    private final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();
    public record Result(int code, byte[] output, String error) {
        public String text() { return new String(output, StandardCharsets.UTF_8); }
        public Result require() {
            if (code != 0) throw new GitProblem(422, error.isBlank() ? "Git failed (exit " + code + "). Check repository configuration." : error.strip());
            return this;
        }
    }
    public Result run(Path root, String... args) { return run(root, null, Map.of(), false, args); }
    public String text(Path root, String... args) { return run(root, args).require().text().replaceFirst("\\n$", ""); }
    public Result run(Path root, byte[] input, Map<String, String> environment, boolean remote, String... args) {
        List<String> command = new ArrayList<>(List.of("git", "-c", "color.ui=false", "-c", "core.quotePath=false"));
        if (args.length > 0 && Set.of("add", "restore", "rm", "ls-files", "diff", "update-index").contains(args[0])) command.add("--literal-pathspecs");
        command.addAll(List.of(args));
        Process process = null; Semaphore limit = remote ? remotes : reads; boolean permit = false;
        try {
            limit.acquire(); permit = true;
            ProcessBuilder builder = new ProcessBuilder(command).directory(root.toFile());
            // Repository identities come from discovery, never inherited shell routing variables.
            for (String key : List.of("GIT_DIR", "GIT_WORK_TREE", "GIT_COMMON_DIR", "GIT_INDEX_FILE", "GIT_OBJECT_DIRECTORY", "GIT_ALTERNATE_OBJECT_DIRECTORIES")) builder.environment().remove(key);
            builder.environment().put("GIT_TERMINAL_PROMPT", "0");
            builder.environment().put("GCM_INTERACTIVE", "never");
            builder.environment().put("GIT_OPTIONAL_LOCKS", "0");
            builder.environment().put("GIT_EDITOR", "true");
            builder.environment().putAll(environment);
            process = builder.start();
            final Process running = process;
            Future<byte[]> stdout = io.submit(() -> read(running.getInputStream()));
            Future<byte[]> stderr = io.submit(() -> read(running.getErrorStream()));
            Future<?> writer = io.submit(() -> { try (var stream = running.getOutputStream()) { if (input != null) stream.write(input); } return null; });
            if (!process.waitFor(remote ? remoteTimeout : localTimeout, TimeUnit.SECONDS))
                throw new GitProblem(504, "Git timed out. The operation may have completed; refresh repository state before trying again.");
            writer.get(2, TimeUnit.SECONDS);
            return new Result(process.exitValue(), stdout.get(2, TimeUnit.SECONDS), new String(stderr.get(2, TimeUnit.SECONDS), StandardCharsets.UTF_8));
        } catch (GitProblem e) { throw e;
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new GitProblem(503, "Git interrupted. Refresh repository state before trying again.");
        } catch (Exception e) { throw new GitProblem(422, "Unable to execute Git: " + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage()));
        } finally {
            if (permit) limit.release();
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
    }
    private byte[] read(InputStream stream) throws IOException {
        try (stream) {
            byte[] bytes = stream.readNBytes(outputLimit + 1);
            if (bytes.length > outputLimit) throw new IOException("Git output exceeds the configured limit. Select a smaller file or increase workspace.git.output-limit-bytes.");
            return bytes;
        }
    }
    @PreDestroy public void close() { io.shutdownNow(); }
}
