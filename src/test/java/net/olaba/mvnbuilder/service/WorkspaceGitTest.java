package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.service.git.*;
import static net.olaba.mvnbuilder.service.git.GitModels.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

class WorkspaceGitTest {
    @TempDir Path base;
    GitCommandRunner git;
    WorkspaceGitDiscoveryService discovery;
    GitChangeService changes;
    GitRepositoryActionService actions;
    Workspace workspace;
    Resolved repository;
    Path root;
    @BeforeEach void setup() throws Exception {
        git = new GitCommandRunner(); discovery = new WorkspaceGitDiscoveryService(git);
        changes = new GitChangeService(git); actions = new GitRepositoryActionService(discovery, changes, git);
        workspace = new Workspace(); workspace.setId(1L); workspace.setName("Fixture"); workspace.setBasePath(base.toString());
        root = Files.createDirectory(base.resolve("plain-repository"));
        git.text(root, "init", "-b", "main"); git.text(root, "config", "user.name", "Fixture"); git.text(root, "config", "user.email", "fixture@example.invalid");
        git.text(root, "config", "core.autocrlf", "false");
        rediscover();
    }
    @AfterEach void close() { actions.close(); git.close(); }
    void rediscover() { var result = discovery.discover(workspace); repository = discovery.repository(workspace, result.repositories().stream().filter(r -> r.path().equals("plain-repository")).findFirst().orElseThrow().id()); }
    void write(String name, String content) throws Exception { Path file = root.resolve(name); if (file.getParent() != null) Files.createDirectories(file.getParent()); Files.writeString(file, content); }
    void commitBase(String content) throws Exception { write("file.txt", content); git.text(root, "add", "--", "file.txt"); git.text(root, "commit", "-m", "Base"); }
    String index(String path) { return new String(git.run(root, "show", ":" + path).require().output(), StandardCharsets.UTF_8); }
    void select(String path, boolean stage, java.util.function.Predicate<Line> predicate) {
        String area = stage ? "changes" : "staged"; Diff diff = changes.diff(repository, path, area);
        var selected = diff.lines().stream().filter(predicate).map(Line::id).toList(); assertFalse(selected.isEmpty(), "fixture selection must not be empty");
        changes.select(repository, new Selection(path, area, diff.version(), selected, false), stage);
    }
    Operation await(Operation operation) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (operation.state().equals("running") && System.nanoTime() < deadline) { Thread.sleep(10); operation = actions.operation(workspace, operation.id()); }
        assertEquals("done", operation.state()); return operation;
    }
    void success(Operation operation) throws Exception { var result = await(operation); assertTrue(result.outcomes().stream().allMatch(o -> o.state().equals("success")), result.toString()); }
    Action action(String action, String path, boolean untracked, String confirmation) {
        return new Action(action, List.of(new Target(repository.descriptor().id(), changes.status(repository).version())), path, untracked, confirmation);
    }
    @Test void discoversNonMavenNestedAndLinkedWorktreesWithoutSymlinks() throws Exception {
        commitBase("base\n");
        Path nested = Files.createDirectory(root.resolve("nested")); git.text(nested, "init", "-b", "main");
        Path worktree = base.resolve("linked"); git.text(root, "worktree", "add", "-b", "other", worktree.toString());
        Files.createSymbolicLink(base.resolve("alias"), root);
        var result = discovery.discover(workspace);
        assertEquals(3, result.repositories().size()); assertTrue(result.complete());
        var linked = discovery.repository(workspace, result.repositories().stream().filter(r -> r.path().equals("linked")).findFirst().orElseThrow().id());
        assertEquals(repository.commonDir(), linked.commonDir()); assertNotEquals(repository.descriptor().id(), linked.descriptor().id());
        Workspace another = new Workspace(); another.setId(2L); another.setBasePath(base.toString());
        assertThrows(GitProblem.class, () -> discovery.repository(another, repository.descriptor().id()));
        assertThrows(GitProblem.class, () -> WorkspaceGitDiscoveryService.safePath(repository, "../outside"));
        assertThrows(GitProblem.class, () -> WorkspaceGitDiscoveryService.safePath(repository, ".git/config"));
        assertThrows(GitProblem.class, () -> WorkspaceGitDiscoveryService.safePath(repository, "nested/file"));
    }
    @Test void firstCommitAndPartialNewFileThenUnstageLastAddition() throws Exception {
        write("file.txt", "first\nsecond\n");
        assertTrue(changes.status(repository).unborn());
        select("file.txt", true, line -> line.text().equals("second"));
        assertEquals("second\n", index("file.txt")); assertEquals("first\nsecond\n", Files.readString(root.resolve("file.txt")));
        select("file.txt", false, line -> line.type().equals("+"));
        assertTrue(git.text(root, "ls-files").isEmpty());
        Diff diff = changes.diff(repository, "file.txt", "changes");
        changes.select(repository, new Selection("file.txt", "changes", diff.version(), null, false), true);
        success(actions.commit(workspace, repository.descriptor().id(), new Commit("Initial", changes.stagedVersion(repository))));
        assertFalse(changes.status(repository).unborn());
    }
    @Test void selectIndividualLinesWithinReplacementAndPreserveOtherPaths() throws Exception {
        commitBase("a\nb\nc\nd\n"); write("file.txt", "a\nB\nextra\nc\nd\n");
        write("other", "untracked\n");
        select("file.txt", true, line -> line.type().equals("+") && line.text().equals("extra"));
        assertEquals("a\nb\nextra\nc\nd\n", index("file.txt"));
        select("file.txt", true, line -> line.type().equals("-") && line.text().equals("b"));
        assertEquals("a\nextra\nc\nd\n", index("file.txt"));
        assertEquals("a\nB\nextra\nc\nd\n", Files.readString(root.resolve("file.txt")));
        select("file.txt", false, line -> line.type().equals("+") && line.text().equals("extra"));
        assertEquals("a\nc\nd\n", index("file.txt")); assertTrue(Files.exists(root.resolve("other")));
    }
    @Test void preservesCrlfAndMissingFinalNewlineAndSpecialNames() throws Exception {
        String name = "quote\" tab\t newline\n.txt"; write(name, "a\r\nb\r\nend");
        git.text(root, "add", "--", name); git.text(root, "commit", "-m", "Base");
        write(name, "a\r\nB\r\nend!");
        select(name, true, line -> line.text().equals("B\r") || line.text().equals("b\r"));
        assertEquals("a\r\nB\r\nend", index(name));
        assertEquals("a\r\nB\r\nend!", Files.readString(root.resolve(name)));
    }
    @Test void addsAtBeginningAndEndAndAcrossSeparateBlocks() throws Exception {
        commitBase("one\n" + "context\n".repeat(12) + "last\n");
        write("file.txt", "start\none\n" + "context\n".repeat(12) + "last\nend\n");
        Diff diff = changes.diff(repository, "file.txt", "changes");
        assertTrue(diff.lines().stream().map(Line::block).distinct().count() > 1);
        select("file.txt", true, line -> line.type().equals("+"));
        assertEquals(Files.readString(root.resolve("file.txt")), index("file.txt"));
    }
    @Test void staleSelectionAndIndexLockNeverStageBroaderChanges() throws Exception {
        commitBase("old\n"); write("file.txt", "new\n"); Diff diff = changes.diff(repository, "file.txt", "changes");
        write("file.txt", "externally changed\n");
        var request = new Selection("file.txt", "changes", diff.version(), null, false);
        assertEquals(409, assertThrows(GitProblem.class, () -> changes.select(repository, request, true)).status()); assertEquals("old\n", index("file.txt"));
        Files.writeString(repository.gitDir().resolve("index.lock"), "external lock");
        assertThrows(GitProblem.class, () -> changes.select(repository, new Selection("file.txt", "changes", changes.status(repository).version(), null, false), true));
        assertEquals("external lock", Files.readString(repository.gitDir().resolve("index.lock"))); assertEquals("old\n", index("file.txt"));
    }
    @Test void deletedFileAndEmptyTreeCommitAreSupported() throws Exception {
        commitBase("old\n"); Files.delete(root.resolve("file.txt"));
        Diff diff = changes.diff(repository, "file.txt", "changes");
        changes.select(repository, new Selection("file.txt", "changes", diff.version(), null, false), true);
        assertTrue(git.text(root, "ls-files").isEmpty());
        success(actions.commit(workspace, repository.descriptor().id(), new Commit("Delete all", changes.stagedVersion(repository))));
        assertTrue(changes.status(repository).files().isEmpty());
    }
    @Test void discardRestoresIndexAndKeepsStagedAndUntrackedFiles() throws Exception {
        commitBase("base\n"); write("file.txt", "staged\n"); git.text(root, "add", "file.txt");
        write("file.txt", "unstaged\n"); write("untracked", "keep\n");
        success(actions.actions(workspace, action("discard", "file.txt", false, "confirmed")));
        assertEquals("staged\n", index("file.txt")); assertEquals("staged\n", Files.readString(root.resolve("file.txt"))); assertTrue(Files.exists(root.resolve("untracked")));
    }
    @Test void commitIncludesOnlyIndexAndRejectsNoChangesAndStaleIndex() throws Exception {
        commitBase("base\n"); write("file.txt", "staged\n"); git.text(root, "add", "file.txt"); write("file.txt", "unstaged\n");
        String version = changes.stagedVersion(repository);
        success(actions.commit(workspace, repository.descriptor().id(), new Commit("Only staged", version)));
        assertEquals("staged", git.text(root, "show", "HEAD:file.txt")); assertEquals("unstaged\n", Files.readString(root.resolve("file.txt")));
        assertThrows(GitProblem.class, () -> actions.commit(workspace, repository.descriptor().id(), new Commit("stale", version)));
        assertThrows(GitProblem.class, () -> actions.commit(workspace, repository.descriptor().id(), new Commit("empty", changes.stagedVersion(repository))));
    }
    @Test void binaryAndSymlinkHaveExplicitStates() throws Exception {
        Files.write(root.resolve("binary"), new byte[]{0, 1, 2});
        Files.createSymbolicLink(root.resolve("link"), base.resolve("does-not-exist"));
        assertFalse(changes.diff(repository, "binary", "changes").partial());
        assertFalse(changes.diff(repository, "link", "changes").partial());
    }
    @Test void remoteActionsUseLocalRemotesAndRefuseDivergenceAndForcePush() throws Exception {
        commitBase("base\n"); Path remote = base.resolve("remote.git");
        git.text(base, "init", "--bare", remote.toString()); git.text(root, "remote", "add", "origin", remote.toString());
        git.text(root, "push", "-u", "origin", "main");
        Path peer = base.resolve("peer"); git.text(base, "clone", "-b", "main", remote.toString(), peer.toString());
        git.text(peer, "config", "user.name", "Fixture"); git.text(peer, "config", "user.email", "fixture@example.invalid");
        Files.writeString(peer.resolve("remote.txt"), "remote\n"); git.text(peer, "add", "remote.txt"); git.text(peer, "commit", "-m", "Remote"); git.text(peer, "push");
        success(actions.actions(workspace, action("fetch", null, false, null)));
        assertEquals(1, changes.status(repository).behind());
        success(actions.actions(workspace, action("pull", null, false, null)));
        assertTrue(Files.exists(root.resolve("remote.txt")));
        write("local.txt", "local\n"); git.text(root, "add", "local.txt"); git.text(root, "commit", "-m", "Local");
        Files.writeString(peer.resolve("peer.txt"), "peer\n"); git.text(peer, "add", "peer.txt"); git.text(peer, "commit", "-m", "Peer"); git.text(peer, "push");
        String before = git.text(root, "rev-parse", "HEAD");
        assertEquals("failed", await(actions.actions(workspace, action("pull", null, false, null))).outcomes().getFirst().state());
        assertEquals(before, git.text(root, "rev-parse", "HEAD"));
        assertEquals("failed", await(actions.actions(workspace, action("push", null, false, null))).outcomes().getFirst().state());
    }
    @Test void stashChoicesUnstageAllAndDuplicateTargetsPreserveBoundaries() throws Exception {
        commitBase("base\n"); write("file.txt", "modified\n"); write("new.txt", "keep\n");
        success(actions.actions(workspace, action("stash", null, false, "confirmed")));
        assertTrue(Files.exists(root.resolve("new.txt"))); assertEquals("base\n", Files.readString(root.resolve("file.txt")));
        var target = new Target(repository.descriptor().id(), changes.status(repository).version());
        var duplicate = await(actions.actions(workspace, new Action("stash", List.of(target, target), null, true, "confirmed")));
        assertEquals(1, duplicate.outcomes().size()); assertEquals("success", duplicate.outcomes().getFirst().state());
        assertFalse(Files.exists(root.resolve("new.txt"))); assertEquals(2, git.text(root, "stash", "list").lines().count());
        write("file.txt", "stage\n"); git.text(root, "add", "file.txt");
        success(actions.actions(workspace, action("unstage-all", null, false, null)));
        assertEquals("stage\n", Files.readString(root.resolve("file.txt"))); assertEquals("base\n", index("file.txt"));
    }
    @Test void renameMetadataAndPartialDeletedFilesAndConflictsAreExplicit() throws Exception {
        commitBase("a\nb\nc\n"); git.text(root, "mv", "file.txt", "renamed.txt");
        var rename = changes.diff(repository, "renamed.txt", "staged"); assertFalse(rename.partial()); assertEquals("file.txt", rename.oldPath());
        changes.select(repository, new Selection("renamed.txt", "staged", rename.version(), null, false), false);
        assertEquals("a\nb\nc\n", index("file.txt"));
        // Restore the fixture to its original pathname without changing the committed base.
        Files.move(root.resolve("renamed.txt"), root.resolve("file.txt")); Files.delete(root.resolve("file.txt"));
        select("file.txt", true, line -> line.type().equals("-") && line.text().equals("b"));
        assertEquals("a\nc\n", index("file.txt")); assertFalse(Files.exists(root.resolve("file.txt")));
    }
    @Test void staleDiscardAndInvalidSelectionsDoNotMutateFiles() throws Exception {
        commitBase("base\n"); write("file.txt", "first\n"); var action = action("discard", "file.txt", false, "confirmed");
        write("file.txt", "second\n"); assertEquals("failed", await(actions.actions(workspace, action)).outcomes().getFirst().state());
        assertEquals("second\n", Files.readString(root.resolve("file.txt")));
        var diff = changes.diff(repository, "file.txt", "changes");
        assertThrows(GitProblem.class, () -> changes.select(repository, new Selection("file.txt", "changes", diff.version(), List.of("invented"), false), true));
        assertEquals("base\n", index("file.txt"));
        assertThrows(GitProblem.class, () -> changes.select(repository, new Selection("file.txt", "changes", diff.version(), List.of(), false), true));
        assertEquals("base\n", index("file.txt"));
    }
    @Test void unresolvedConflictsAreExplicitAndWholeFileResolutionCanBeStaged() throws Exception {
        commitBase("base\n"); git.text(root, "checkout", "-b", "other"); write("file.txt", "other\n"); git.text(root, "commit", "-am", "Other");
        git.text(root, "checkout", "main"); write("file.txt", "main\n"); git.text(root, "commit", "-am", "Main");
        assertNotEquals(0, git.run(root, "merge", "other").code());
        var diff = changes.diff(repository, "file.txt", "changes"); assertEquals("conflict", diff.kind()); assertFalse(diff.partial());
        assertThrows(GitProblem.class, () -> actions.commit(workspace, repository.descriptor().id(), new Commit("unresolved", changes.stagedVersion(repository))));
        write("file.txt", "resolved\n"); diff = changes.diff(repository, "file.txt", "changes");
        changes.select(repository, new Selection("file.txt", "changes", diff.version(), null, false), true);
        assertTrue(changes.status(repository).files().stream().noneMatch(f -> f.kind().equals("conflict")));
        success(actions.commit(workspace, repository.descriptor().id(), new Commit("Resolved", changes.stagedVersion(repository))));
    }
    @Test void representativeDiscoveryAndStatusMeasurement() throws Exception {
        for (int i = 0; i < 50; i++) {
            Path project = Files.createDirectory(base.resolve("repository-" + i)); git.text(project, "init", "-b", "main");
            for (int j = 0; j < 4; j++) Files.createDirectories(project.resolve("module-" + j).resolve("src/main/java"));
        }
        long start = System.nanoTime(); var found = discovery.discover(workspace); long scan = System.nanoTime() - start;
        start = System.nanoTime();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = found.repositories().stream().map(repo -> executor.submit(() -> changes.status(discovery.repository(workspace, repo.id())))).toList();
            for (var future : futures) assertNotNull(future.get());
        }
        assertEquals(51, found.repositories().size()); assertTrue(found.complete());
        System.out.printf("Git fixture: %d repositories, %d directories, discovery %.0f ms, bounded status %.0f ms%n", found.repositories().size(), found.visited(), scan / 1e6, (System.nanoTime() - start) / 1e6);
    }
    @Test void honorsEolConversionAndDisablesPartialContentFilters() throws Exception {
        commitBase("old\n"); git.text(root, "config", "core.autocrlf", "true");
        write("file.txt", "new\r\nextra\r\n");
        select("file.txt", true, line -> line.text().equals("old") || line.text().equals("new"));
        assertEquals("new\n", index("file.txt"));
        assertEquals("new\r\nextra\r\n", Files.readString(root.resolve("file.txt")));
        write(".gitattributes", "file.txt filter=custom\n");
        var diff = changes.diff(repository, "file.txt", "changes"); assertFalse(diff.partial()); assertTrue(diff.reason().contains("filters"));
    }
    @Test void incompleteEofReplacementCannotSilentlyJoinLines() throws Exception {
        commitBase("a"); write("file.txt", "a\nb\n");
        var diff = changes.diff(repository, "file.txt", "changes");
        var addedB = diff.lines().stream().filter(line -> line.type().equals("+") && line.text().equals("b")).map(Line::id).toList();
        assertThrows(GitProblem.class, () -> changes.select(repository, new Selection("file.txt", "changes", diff.version(), addedB, false), true));
        assertEquals("a", index("file.txt"));
        select("file.txt", true, line -> !line.type().equals(" "));
        assertEquals("a\nb\n", index("file.txt"));
    }
    @Test void selectedCommitPreservesExcludedStagingAndWorkingFiles() throws Exception {
        commitBase("base\n"); write("second.txt", "base two\n"); git.text(root, "add", "second.txt"); git.text(root, "commit", "-m", "Second base");
        write("file.txt", "selected\n"); write("second.txt", "staged but excluded\n"); git.text(root, "add", "second.txt");
        write("second.txt", "additional unstaged\n"); write("new.txt", "selected new\n"); write("other.txt", "unselected new\n");
        String excludedEntry = git.text(root, "ls-files", "--stage", "--", "second.txt");
        var target = new CommitTarget(repository.descriptor().id(), changes.status(repository).version(),
                List.of(new CommitFile("file.txt", "changes"), new CommitFile("new.txt", "changes")));
        success(actions.commits(workspace, new SelectedCommit("Selected files", List.of(target))));
        assertEquals("selected", git.text(root, "show", "HEAD:file.txt")); assertEquals("selected new", git.text(root, "show", "HEAD:new.txt"));
        assertEquals("base two", git.text(root, "show", "HEAD:second.txt")); assertEquals(excludedEntry, git.text(root, "ls-files", "--stage", "--", "second.txt"));
        assertEquals("additional unstaged\n", Files.readString(root.resolve("second.txt"))); assertTrue(Files.exists(root.resolve("other.txt")));
        assertEquals("selected\n", index("file.txt")); assertFalse(Files.exists(repository.gitDir().resolve("index.lock")));
    }
    @Test void selectedStagedVersionKeepsAdditionalUnstagedEditsAndSupportsInitialCommit() throws Exception {
        write("file.txt", "staged\n"); git.text(root, "add", "file.txt"); write("file.txt", "working\n");
        write("excluded.txt", "not included\n"); git.text(root, "add", "excluded.txt");
        var target = new CommitTarget(repository.descriptor().id(), changes.status(repository).version(), List.of(new CommitFile("file.txt", "staged")));
        success(actions.commits(workspace, new SelectedCommit("Initial subset", List.of(target))));
        assertEquals("staged", git.text(root, "show", "HEAD:file.txt")); assertEquals("working\n", Files.readString(root.resolve("file.txt")));
        assertNotEquals(0, git.run(root, "cat-file", "-e", "HEAD:excluded.txt").code()); assertEquals("not included\n", index("excluded.txt"));
    }
    @Test void selectedCommitUpdatesTrackedFilesUnderIgnoredDirectoriesOnly() throws Exception {
        String selected = "module/target/selected.jar", excluded = "module/target/excluded.jar";
        write(selected, "base\n"); write(excluded, "excluded base\n"); git.text(root, "add", "."); git.text(root, "commit", "-m", "Tracked artifacts");
        write(".gitignore", "**/target/\n"); write(selected, "selected working\n");
        write(excluded, "excluded staged\n"); git.text(root, "add", "-u"); write(selected, "selected latest\n");
        write("module/target/new-ignored.jar", "never tracked\n");
        String excludedEntry = git.text(root, "ls-files", "--stage", "--", excluded);
        assertTrue(changes.status(repository).files().stream().noneMatch(file -> file.path().endsWith("new-ignored.jar")));
        var target = new CommitTarget(repository.descriptor().id(), changes.status(repository).version(), List.of(new CommitFile(selected, "changes")));
        success(actions.commits(workspace, new SelectedCommit("Update tracked artifact", List.of(target))));
        assertEquals("selected latest", git.text(root, "show", "HEAD:" + selected));
        assertEquals("excluded base", git.text(root, "show", "HEAD:" + excluded));
        assertEquals(excludedEntry, git.text(root, "ls-files", "--stage", "--", excluded));
        assertNotEquals(0, git.run(root, "cat-file", "-e", "HEAD:module/target/new-ignored.jar").code());
        assertEquals("never tracked\n", Files.readString(root.resolve("module/target/new-ignored.jar")));
    }
    @Test void wholeFileStagingUpdatesAndDeletesTrackedFilesUnderIgnoredDirectories() throws Exception {
        String path = "module/target/artifact.jar";
        write(path, "base\n"); git.text(root, "add", "."); git.text(root, "commit", "-m", "Tracked artifact");
        write(".gitignore", "**/target/\n"); write(path, "working\n"); write("module/target/new-ignored.jar", "ignored\n");
        changes.select(repository, new Selection(path, "changes", changes.status(repository).version(), null, false), true);
        assertEquals("working\n", index(path)); assertEquals("working\n", Files.readString(root.resolve(path)));
        Files.delete(root.resolve(path));
        changes.select(repository, new Selection(path, "changes", changes.status(repository).version(), null, false), true);
        assertTrue(git.text(root, "ls-files", "--", path).isEmpty());
        assertTrue(git.text(root, "ls-files", "--", "module/target/new-ignored.jar").isEmpty());
    }
    @Test void selectedWorkingCommitSupportsIgnoredFilesAlreadyAddedToTheRealIndex() throws Exception {
        String path = "module/target/artifact.jar";
        write(".gitignore", "**/target/\n"); write(path, "staged\n"); git.text(root, "add", "-f", "--", path); write(path, "working\n");
        write("module/target/new-ignored.jar", "ignored\n");
        var target = new CommitTarget(repository.descriptor().id(), changes.status(repository).version(), List.of(new CommitFile(path, "changes")));
        success(actions.commits(workspace, new SelectedCommit("Initial tracked selection", List.of(target))));
        assertEquals("working", git.text(root, "show", "HEAD:" + path));
        assertEquals(path, git.text(root, "ls-files")); assertTrue(git.text(root, "diff", "--cached", "--name-only").isEmpty());
        assertNotEquals(0, git.run(root, "cat-file", "-e", "HEAD:module/target/new-ignored.jar").code());
    }
    @Test void selectedRenameAndDeletionCommitAtomically() throws Exception {
        commitBase("base\n"); git.text(root, "mv", "file.txt", "renamed.txt");
        var target = new CommitTarget(repository.descriptor().id(), changes.status(repository).version(), List.of(new CommitFile("renamed.txt", "staged")));
        success(actions.commits(workspace, new SelectedCommit("Rename", List.of(target))));
        assertNotEquals(0, git.run(root, "cat-file", "-e", "HEAD:file.txt").code()); assertEquals("base", git.text(root, "show", "HEAD:renamed.txt"));
        Files.delete(root.resolve("renamed.txt"));
        target = new CommitTarget(repository.descriptor().id(), changes.status(repository).version(), List.of(new CommitFile("renamed.txt", "changes")));
        success(actions.commits(workspace, new SelectedCommit("Delete selected", List.of(target))));
        assertTrue(git.text(root, "ls-tree", "HEAD").isEmpty()); assertTrue(git.text(root, "ls-files").isEmpty());
    }
    @Test void selectedCommitBatchReportsHookFailureWithoutLosingOtherResults() throws Exception {
        commitBase("base\n"); write("file.txt", "first selected\n");
        Path second = Files.createDirectory(base.resolve("second")); git.text(second, "init", "-b", "main");
        git.text(second, "config", "user.name", "Fixture"); git.text(second, "config", "user.email", "fixture@example.invalid");
        Files.writeString(second.resolve("file.txt"), "second selected\n"); git.text(second, "add", "file.txt");
        Path hook = second.resolve(".git/hooks/pre-commit"); Files.writeString(hook, "#!/bin/sh\necho 'Fixture hook refused commit' >&2\nexit 1\n"); assertTrue(hook.toFile().setExecutable(true));
        var found = discovery.discover(workspace); String id = found.repositories().stream().filter(repo -> repo.path().equals("second")).findFirst().orElseThrow().id();
        var other = discovery.repository(workspace, id); String originalIndex = GitChangeService.fileHash(other.gitDir().resolve("index"));
        var firstTarget = new CommitTarget(repository.descriptor().id(), changes.status(repository).version(), List.of(new CommitFile("file.txt", "changes")));
        var secondTarget = new CommitTarget(id, changes.status(other).version(), List.of(new CommitFile("file.txt", "staged")));
        var result = await(actions.commits(workspace, new SelectedCommit("Batch selection", List.of(firstTarget, secondTarget))));
        assertEquals("success", result.outcomes().stream().filter(o -> o.repositoryId().equals(firstTarget.id())).findFirst().orElseThrow().state());
        assertEquals("failed", result.outcomes().stream().filter(o -> o.repositoryId().equals(id)).findFirst().orElseThrow().state());
        assertEquals("first selected", git.text(root, "show", "HEAD:file.txt")); assertEquals(originalIndex, GitChangeService.fileHash(other.gitDir().resolve("index")));
        assertEquals("unborn", changes.head(other)); assertFalse(Files.exists(other.gitDir().resolve("index.lock")));
    }
    @Test void selectedCommitRejectsStaleDuplicateAndUnfinishedOperationTargets() throws Exception {
        commitBase("base\n"); write("file.txt", "changed\n");
        var target = new CommitTarget(repository.descriptor().id(), changes.status(repository).version(), List.of(new CommitFile("file.txt", "changes")));
        write("file.txt", "external\n");
        assertThrows(GitProblem.class, () -> actions.commits(workspace, new SelectedCommit("Stale", List.of(target))));
        var duplicate = new CommitTarget(repository.descriptor().id(), changes.status(repository).version(), List.of(new CommitFile("file.txt", "changes"), new CommitFile("file.txt", "changes")));
        assertThrows(GitProblem.class, () -> actions.commits(workspace, new SelectedCommit("Duplicate", List.of(duplicate))));
        Files.writeString(repository.gitDir().resolve("CHERRY_PICK_HEAD"), changes.head(repository));
        var current = new CommitTarget(repository.descriptor().id(), changes.status(repository).version(), List.of(new CommitFile("file.txt", "changes")));
        assertThrows(GitProblem.class, () -> actions.commits(workspace, new SelectedCommit("In progress", List.of(current))));
        assertEquals("base\n", index("file.txt"));
    }
    @Test void selectedFilesCommitAndPushAcrossTwoRepositories() throws Exception {
        commitBase("base\n");
        Path second = Files.createDirectory(base.resolve("second")); git.text(second, "init", "-b", "main");
        git.text(second, "config", "user.name", "Fixture"); git.text(second, "config", "user.email", "fixture@example.invalid");
        Files.writeString(second.resolve("file.txt"), "second base\n"); git.text(second, "add", "file.txt"); git.text(second, "commit", "-m", "Second base");
        Path remote1 = base.resolve("remote1.git"), remote2 = base.resolve("remote2.git");
        git.text(base, "init", "--bare", remote1.toString()); git.text(base, "init", "--bare", remote2.toString());
        git.text(root, "remote", "add", "origin", remote1.toString()); git.text(root, "push", "-u", "origin", "main");
        git.text(second, "remote", "add", "origin", remote2.toString()); git.text(second, "push", "-u", "origin", "main");
        write("file.txt", "first selected\n"); write("new.txt", "first new selected\n"); write("excluded.txt", "first excluded\n"); git.text(root, "add", "excluded.txt");
        Files.writeString(second.resolve("file.txt"), "second staged\n"); git.text(second, "add", "file.txt"); Files.writeString(second.resolve("file.txt"), "second unstaged\n");
        var found = discovery.discover(workspace); String id = found.repositories().stream().filter(repo -> repo.path().equals("second")).findFirst().orElseThrow().id();
        var other = discovery.repository(workspace, id);
        var one = new CommitTarget(repository.descriptor().id(), changes.status(repository).version(), List.of(new CommitFile("file.txt", "changes"), new CommitFile("new.txt", "changes")));
        var two = new CommitTarget(id, changes.status(other).version(), List.of(new CommitFile("file.txt", "staged")));
        success(actions.commits(workspace, new SelectedCommit("Shared selected message", List.of(one, two))));
        assertEquals("Shared selected message", git.text(root, "log", "-1", "--format=%s")); assertEquals("Shared selected message", git.text(second, "log", "-1", "--format=%s"));
        success(actions.actions(workspace, new Action("push", List.of(new Target(one.id(), changes.status(repository).version()), new Target(id, changes.status(other).version())), null, false, null)));
        assertEquals("first selected", git.text(remote1, "show", "main:file.txt")); assertEquals("first new selected", git.text(remote1, "show", "main:new.txt"));
        assertNotEquals(0, git.run(remote1, "cat-file", "-e", "main:excluded.txt").code()); assertEquals("first excluded\n", index("excluded.txt"));
        assertEquals("second staged", git.text(remote2, "show", "main:file.txt")); assertEquals("second unstaged\n", Files.readString(second.resolve("file.txt")));
    }
    @Test void directoryReplacementCannotCommitAnUnselectedDeletion() throws Exception {
        commitBase("original\n"); Files.delete(root.resolve("file.txt")); git.text(root, "add", "-u");
        Files.createDirectory(root.resolve("file.txt")); Files.writeString(root.resolve("file.txt/child.txt"), "unrelated new content\n");
        String before = changes.head(repository); String indexBefore = GitChangeService.fileHash(repository.gitDir().resolve("index"));
        var target = new CommitTarget(repository.descriptor().id(), changes.status(repository).version(), List.of(new CommitFile("file.txt/child.txt", "changes")));
        var result = await(actions.commits(workspace, new SelectedCommit("Only child", List.of(target))));
        assertEquals("failed", result.outcomes().getFirst().state()); assertEquals(before, changes.head(repository));
        assertEquals(indexBefore, GitChangeService.fileHash(repository.gitDir().resolve("index"))); assertTrue(Files.exists(root.resolve("file.txt/child.txt")));
    }
    @Test void sharedCoordinatorSerializesWorktreesAndAllowsIndependentWork() throws Exception {
        commitBase("base\n"); Path linked = base.resolve("linked"); git.text(root, "worktree", "add", "-b", "other", linked.toString());
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var second = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> GitMutationCoordinator.mutate(root.toFile(), () -> { entered.countDown(); try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } return null; }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var next = executor.submit(() -> GitMutationCoordinator.mutate(linked.toFile(), () -> { second.countDown(); return null; }));
            assertFalse(second.await(100, TimeUnit.MILLISECONDS)); release.countDown(); first.get(); next.get(); assertEquals(0, second.getCount());
        }
    }
}
