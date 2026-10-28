package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.service.git.*;
import static net.olaba.mvnbuilder.service.git.GitModels.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Process-count and I/O guards for the Git view's hot paths. */
class GitPerformanceTest {
    /** Records every Git invocation so tests can assert how much work a request performs. */
    static class CountingRunner extends GitCommandRunner {
        final List<List<String>> calls = new CopyOnWriteArrayList<>();
        @Override public Result run(Path root, byte[] input, Map<String, String> environment, boolean remote, String... args) {
            calls.add(List.of(args)); return super.run(root, input, environment, remote, args);
        }
        long count(String command) { return calls.stream().filter(args -> args.contains(command)).count(); }
    }
    @TempDir Path base;
    CountingRunner git;
    WorkspaceGitDiscoveryService discovery;
    RegisteredProjects projects;
    GitChangeService changes;
    Workspace workspace;
    Path root;
    @BeforeEach void setup() throws Exception {
        git = new CountingRunner(); projects = new RegisteredProjects(); discovery = new WorkspaceGitDiscoveryService(git, projects); changes = new GitChangeService(git);
        workspace = new Workspace(); workspace.setId(1L); workspace.setName("Fixture"); workspace.setBasePath(base.toString());
        root = Files.createDirectory(base.resolve("repository"));
        git.text(root, "init", "-b", "main"); git.text(root, "config", "user.name", "Fixture"); git.text(root, "config", "user.email", "fixture@example.invalid");
        git.text(root, "config", "core.autocrlf", "false");
        projects.add(root);
    }
    @AfterEach void close() { git.close(); }
    String id() { return discovery.discover(workspace).repositories().getFirst().id(); }

    @Test void cachedRepositoryIsRevalidatedWithoutGitProcesses() throws Exception {
        String id = id(); Resolved first = discovery.repository(workspace, id);
        git.calls.clear();
        Resolved second = discovery.repository(workspace, id);
        assertEquals(0, git.count("rev-parse")); assertEquals(first, second);
        // Re-initializing replaces the Git directory identity, so the cached entry must not be reused.
        Path gitDir = root.resolve(".git");
        try (var files = Files.walk(gitDir)) { files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete()); }
        git.text(root, "init", "-b", "main");
        assertThrows(GitProblem.class, () -> discovery.repository(workspace, id));
    }

    @Test void resolvedCommonDirectorySharesTheLegacyCoordinatorKey() throws Exception {
        Resolved resolved = discovery.repository(workspace, id());
        // Project controls still lock by directory; both entry points must serialize on the same key.
        assertEquals(GitMutationCoordinator.commonDirectory(root.toFile()), resolved.commonDir());
    }

    @Test void listingStatusReadsNoContentAndTracksMetadata() throws Exception {
        List<Path> hashed = new CopyOnWriteArrayList<>();
        GitChangeService counting = new GitChangeService(git) { @Override protected String contentHash(Path file) { hashed.add(file); return super.contentHash(file); } };
        Resolved repository = discovery.repository(workspace, id());
        Path large = root.resolve("large.bin");
        try (var channel = java.nio.channels.FileChannel.open(large, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) { channel.write(java.nio.ByteBuffer.wrap(new byte[]{1}), 50L * 1024 * 1024); }
        Files.writeString(root.resolve("notes.txt"), "first\n");
        Status listed = counting.status(repository);
        assertTrue(hashed.isEmpty(), "listing must not read changed file contents: " + hashed);
        assertEquals(2, listed.files().size());
        assertFalse(counting.status(repository, true).version().equals(listed.version()), "light and full versions must never be interchangeable");
        assertFalse(hashed.isEmpty());
        var modified = Files.getLastModifiedTime(root.resolve("notes.txt"));
        Files.setLastModifiedTime(root.resolve("notes.txt"), java.nio.file.attribute.FileTime.fromMillis(modified.toMillis() + 5000));
        assertNotEquals(listed.version(), counting.status(repository).version());
    }
    @Test void untrackedFoldersAreListedOnceAndCommittedAsTheirFiles() throws Exception {
        Files.writeString(root.resolve("base.txt"), "base\n"); git.text(root, "add", "--", "base.txt"); git.text(root, "commit", "-m", "Base");
        Files.createDirectories(root.resolve("generated/deep"));
        Files.writeString(root.resolve("generated/a.txt"), "a\n"); Files.writeString(root.resolve("generated/deep/b.txt"), "b\n");
        Files.writeString(root.resolve(".gitignore"), "generated/ignored.log\n"); Files.writeString(root.resolve("generated/ignored.log"), "x\n");
        Resolved repository = discovery.repository(workspace, id());
        var folder = changes.status(repository).files().stream().filter(file -> file.path().equals("generated/")).findFirst().orElseThrow();
        assertEquals("untracked-dir", folder.kind());
        assertFalse(changes.status(repository).files().stream().anyMatch(file -> file.path().startsWith("generated/a")));
        assertNotNull(changes.diff(repository, "generated/", "changes").reason());
        var selected = new GitSelectedCommitService(git, changes);
        selected.commit(repository, new CommitTarget(repository.descriptor().id(), changes.status(repository, true).version(), List.of(new CommitFile("generated/", "changes"))), "Add generated");
        assertEquals(List.of("generated/a.txt", "generated/deep/b.txt"), List.of(git.text(root, "show", "--name-only", "--format=", "HEAD").split("\n")));
        assertTrue(Files.exists(root.resolve("generated/ignored.log")));
    }
    @Test void diffUsesABoundedNumberOfGitProcesses() throws Exception {
        Files.writeString(root.resolve("file.txt"), "a\nb\n"); git.text(root, "add", "--", "file.txt"); git.text(root, "commit", "-m", "Base");
        Files.writeString(root.resolve("file.txt"), "a\nB\n"); git.text(root, "add", "--", "file.txt"); Files.writeString(root.resolve("file.txt"), "a\nB\nc\n");
        Resolved repository = discovery.repository(workspace, id());
        git.calls.clear();
        Diff changesDiff = changes.diff(repository, "file.txt", "changes");
        assertTrue(git.calls.size() <= 7, "cold diff started " + git.calls);
        assertEquals(1, changesDiff.lines().stream().filter(line -> line.type().equals("+")).count());
        git.calls.clear(); changes.diff(repository, "file.txt", "changes");
        assertTrue(git.calls.size() <= 6, "warm diff started " + git.calls);
        assertEquals(0, git.count("rev-parse"));
        git.calls.clear();
        Diff stagedDiff = changes.diff(repository, "file.txt", "staged");
        assertTrue(git.calls.size() <= 5, "staged diff started " + git.calls);
        assertEquals("B", stagedDiff.lines().stream().filter(line -> line.type().equals("+")).findFirst().orElseThrow().text());
    }
    @Test void discoveryResolvesProjectsWithoutWalkingBuildFolders() throws Exception {
        for (int i = 0; i < 1000; i++) Files.createDirectories(root.resolve("node_modules/package-" + i));
        Path hidden = Files.createDirectories(root.resolve("target/generated-repository")); git.text(hidden, "init", "-b", "main");
        projects.add(Files.createDirectories(root.resolve("module-a"))); projects.add(Files.createDirectories(root.resolve("module-b")));
        git.calls.clear();
        var result = discovery.discover(workspace);
        assertEquals(List.of("repository"), result.repositories().stream().map(Repository::displayName).toList());
        assertEquals(3, result.visited());
        assertTrue(git.calls.size() <= 3 * 4, "discovery started " + git.calls);
    }
    @Test void membershipIsCheckedWithoutGitProcesses() throws Exception {
        String id = id(); discovery.repository(workspace, id);
        git.calls.clear();
        for (int i = 0; i < 20; i++) discovery.repository(workspace, id);
        assertEquals(0, git.calls.size());
    }
}
