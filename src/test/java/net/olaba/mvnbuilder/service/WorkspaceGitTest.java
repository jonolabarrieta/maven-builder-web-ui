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
    RegisteredProjects projects;
    GitChangeService changes;
    GitRepositoryActionService actions;
    Workspace workspace;
    Resolved repository;
    Path root;
    @BeforeEach void setup() throws Exception {
        git = new GitCommandRunner(); projects = new RegisteredProjects(); discovery = new WorkspaceGitDiscoveryService(git, projects);
        changes = new GitChangeService(git); actions = new GitRepositoryActionService(discovery, changes, git);
        workspace = new Workspace(); workspace.setId(1L); workspace.setName("Fixture"); workspace.setBasePath(base.toString());
        root = Files.createDirectory(base.resolve("plain-repository"));
        git.text(root, "init", "-b", "main"); git.text(root, "config", "user.name", "Fixture"); git.text(root, "config", "user.email", "fixture@example.invalid");
        git.text(root, "config", "core.autocrlf", "false");
        projects.add(root);
        rediscover();
    }
    @AfterEach void close() { actions.close(); git.close(); }
    void rediscover() { var result = discovery.discover(workspace); repository = discovery.repository(workspace, result.repositories().stream().filter(r -> r.name().equals("plain-repository")).findFirst().orElseThrow().id()); }
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
    /** Commits exactly the given marked files of the fixture repository against a fresh snapshot. */
    Operation commitMarked(String message, boolean push, CommitFile... files) {
        return actions.commits(workspace, new SelectedCommit(message, List.of(new CommitTarget(repository.descriptor().id(), changes.status(repository, true).version(), List.of(files))), push));
    }
    void success(Operation operation) throws Exception { var result = await(operation); assertTrue(result.outcomes().stream().allMatch(o -> o.state().equals("success")), result.toString()); }
    Action action(String action, String path, boolean untracked, String confirmation) {
        return new Action(action, List.of(new Target(repository.descriptor().id(), changes.status(repository, true).version())), path, untracked, confirmation);
    }
    @Test void discoversNestedAndLinkedWorktreesOfRegisteredProjectsOnly() throws Exception {
        commitBase("base\n");
        Path nested = Files.createDirectory(root.resolve("nested")); git.text(nested, "init", "-b", "main");
        Path worktree = base.resolve("linked"); git.text(root, "worktree", "add", "-b", "other", worktree.toString());
        Path unregistered = Files.createDirectory(base.resolve("unregistered")); git.text(unregistered, "init", "-b", "main");
        projects.add(nested); projects.add(worktree); projects.add(Files.createDirectories(root.resolve("module/src")));
        var result = discovery.discover(workspace);
        assertEquals(List.of("linked", "nested", "plain-repository"), result.repositories().stream().map(Repository::displayName).toList()); assertTrue(result.complete());
        assertEquals(worktree.toRealPath().toString(), result.repositories().getFirst().absolutePath());
        var linked = discovery.repository(workspace, result.repositories().getFirst().id());
        assertEquals(repository.commonDir(), linked.commonDir()); assertNotEquals(repository.descriptor().id(), linked.descriptor().id());
        Workspace another = new Workspace(); another.setId(2L); another.setBasePath(base.toString());
        assertThrows(GitProblem.class, () -> discovery.repository(another, repository.descriptor().id()));
        assertThrows(GitProblem.class, () -> WorkspaceGitDiscoveryService.safePath(repository, "../outside"));
        assertThrows(GitProblem.class, () -> WorkspaceGitDiscoveryService.safePath(repository, ".git/config"));
        assertThrows(GitProblem.class, () -> WorkspaceGitDiscoveryService.safePath(repository, "nested/file"));
    }
    @Test void projectsOutsideTheBaseAreListedAndRemovedProjectsAreNot(@TempDir Path elsewhere) throws Exception {
        Path outside = Files.createDirectory(elsewhere.resolve("outside")); git.text(outside, "init", "-b", "main");
        Path removed = Files.createDirectory(base.resolve("removed")); git.text(removed, "init", "-b", "main");
        Long outsideId = projects.add(outside); Long removedId = projects.add(removed);
        var result = discovery.discover(workspace);
        assertEquals(List.of("outside", "plain-repository", "removed"), result.repositories().stream().map(Repository::displayName).toList());
        String removedRepository = result.repositories().get(2).id();
        projects.remove(removedId);
        // Membership changes make cached identities unknown until the next discovery.
        assertTrue(discovery.last(workspace).isEmpty());
        assertEquals(List.of("outside", "plain-repository"), discovery.discover(workspace).repositories().stream().map(Repository::displayName).toList());
        assertThrows(GitProblem.class, () -> discovery.repository(workspace, removedRepository));
        assertNotNull(discovery.repository(workspace, repository.descriptor().id()));
        assertTrue(discovery.last(workspace).isPresent());
        workspace.setBasePath(elsewhere.toString());
        assertTrue(discovery.last(workspace).isPresent(), "the base folder no longer bounds the repositories");
        assertNotNull(outsideId);
    }
    @Test void problemProjectsAreReportedWithoutHidingOthers() throws Exception {
        projects.add(base.resolve("missing")); projects.add(Files.createDirectory(base.resolve("plain-folder")));
        Path bare = base.resolve("bare.git"); git.text(base, "init", "--bare", bare.toString()); projects.add(bare);
        var result = discovery.discover(workspace);
        assertEquals(1, result.repositories().size());
        assertEquals(3, result.warnings().size(), result.warnings().toString());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("missing") && w.contains("missing or unreadable")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("plain-folder") && w.contains("not inside a Git repository")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("bare")));
    }
    @Test void equalFolderNamesAreToldApart() {
        var names = WorkspaceGitDiscoveryService.displayNames(List.of(Path.of("/work/a/api"), Path.of("/work/b/api"), Path.of("/work/b/web")));
        assertEquals("a/api", names.get(Path.of("/work/a/api"))); assertEquals("b/api", names.get(Path.of("/work/b/api"))); assertEquals("web", names.get(Path.of("/work/b/web")));
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
        success(commitMarked("Initial", false, new CommitFile("file.txt", "staged")));
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
        assertThrows(GitProblem.class, () -> changes.select(repository, new Selection("file.txt", "changes", changes.status(repository, true).version(), null, false), true));
        assertEquals("external lock", Files.readString(repository.gitDir().resolve("index.lock"))); assertEquals("old\n", index("file.txt"));
    }
    @Test void deletedFileAndEmptyTreeCommitAreSupported() throws Exception {
        commitBase("old\n"); Files.delete(root.resolve("file.txt"));
        Diff diff = changes.diff(repository, "file.txt", "changes");
        changes.select(repository, new Selection("file.txt", "changes", diff.version(), null, false), true);
        assertTrue(git.text(root, "ls-files").isEmpty());
        success(commitMarked("Delete all", false, new CommitFile("file.txt", "staged")));
        assertTrue(changes.status(repository).files().isEmpty());
    }
    @Test void discardRestoresIndexAndKeepsStagedAndUntrackedFiles() throws Exception {
        commitBase("base\n"); write("file.txt", "staged\n"); git.text(root, "add", "file.txt");
        write("file.txt", "unstaged\n"); write("untracked", "keep\n");
        success(actions.actions(workspace, action("discard", "file.txt", false, "confirmed")));
        assertEquals("staged\n", index("file.txt")); assertEquals("staged\n", Files.readString(root.resolve("file.txt"))); assertTrue(Files.exists(root.resolve("untracked")));
    }
    @Test void commitIncludesOnlyMarkedFilesAndRejectsNoChangesAndStaleSnapshots() throws Exception {
        commitBase("base\n"); write("file.txt", "staged\n"); git.text(root, "add", "file.txt"); write("file.txt", "unstaged\n");
        write("other.txt", "staged but not marked\n"); git.text(root, "add", "other.txt");
        String version = changes.status(repository, true).version();
        success(commitMarked("Only marked", false, new CommitFile("file.txt", "staged")));
        assertEquals("staged", git.text(root, "show", "HEAD:file.txt")); assertEquals("unstaged\n", Files.readString(root.resolve("file.txt")));
        assertNotEquals(0, git.run(root, "cat-file", "-e", "HEAD:other.txt").code(), "an unmarked staged file is not committed");
        assertEquals("staged but not marked\n", index("other.txt"));
        var stale = new CommitTarget(repository.descriptor().id(), version, List.of(new CommitFile("other.txt", "staged")));
        assertThrows(GitProblem.class, () -> actions.commits(workspace, new SelectedCommit("stale", List.of(stale))));
        commitBase("base\n");
        assertThrows(GitProblem.class, () -> commitMarked("empty", false, new CommitFile("file.txt", "changes")));
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
        var target = new Target(repository.descriptor().id(), changes.status(repository, true).version());
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
        assertThrows(GitProblem.class, () -> commitMarked("unresolved", false, new CommitFile("file.txt", "changes")));
        write("file.txt", "resolved\n"); diff = changes.diff(repository, "file.txt", "changes");
        changes.select(repository, new Selection("file.txt", "changes", diff.version(), null, false), true);
        assertTrue(changes.status(repository).files().stream().noneMatch(f -> f.kind().equals("conflict")));
        // Concluding the merge happens outside the Git view; the marked-file commit refuses it.
        var refused = assertThrows(GitProblem.class, () -> commitMarked("Resolved", false, new CommitFile("file.txt", "staged")));
        assertTrue(refused.getMessage().contains("outside the Git view"), refused.getMessage());
    }
    @Test void representativeDiscoveryAndStatusMeasurement() throws Exception {
        for (int i = 0; i < 50; i++) {
            Path project = Files.createDirectory(base.resolve("repository-" + i)); git.text(project, "init", "-b", "main"); projects.add(project);
            for (int j = 0; j < 4; j++) Files.createDirectories(project.resolve("module-" + j).resolve("src/main/java"));
        }
        long start = System.nanoTime(); var found = discovery.discover(workspace); long scan = System.nanoTime() - start;
        start = System.nanoTime();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = found.repositories().stream().map(repo -> executor.submit(() -> changes.status(discovery.repository(workspace, repo.id())))).toList();
            for (var future : futures) assertNotNull(future.get());
        }
        assertEquals(51, found.repositories().size()); assertTrue(found.complete());
        System.out.printf("Git fixture: %d repositories, %d projects, discovery %.0f ms, bounded status %.0f ms%n", found.repositories().size(), found.visited(), scan / 1e6, (System.nanoTime() - start) / 1e6);
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
        var target = new CommitTarget(repository.descriptor().id(), changes.status(repository, true).version(),
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
        var target = new CommitTarget(repository.descriptor().id(), changes.status(repository, true).version(), List.of(new CommitFile("file.txt", "staged")));
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
        var target = new CommitTarget(repository.descriptor().id(), changes.status(repository, true).version(), List.of(new CommitFile(selected, "changes")));
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
        changes.select(repository, new Selection(path, "changes", changes.status(repository, true).version(), null, false), true);
        assertEquals("working\n", index(path)); assertEquals("working\n", Files.readString(root.resolve(path)));
        Files.delete(root.resolve(path));
        changes.select(repository, new Selection(path, "changes", changes.status(repository, true).version(), null, false), true);
        assertTrue(git.text(root, "ls-files", "--", path).isEmpty());
        assertTrue(git.text(root, "ls-files", "--", "module/target/new-ignored.jar").isEmpty());
    }
    @Test void selectedWorkingCommitSupportsIgnoredFilesAlreadyAddedToTheRealIndex() throws Exception {
        String path = "module/target/artifact.jar";
        write(".gitignore", "**/target/\n"); write(path, "staged\n"); git.text(root, "add", "-f", "--", path); write(path, "working\n");
        write("module/target/new-ignored.jar", "ignored\n");
        var target = new CommitTarget(repository.descriptor().id(), changes.status(repository, true).version(), List.of(new CommitFile(path, "changes")));
        success(actions.commits(workspace, new SelectedCommit("Initial tracked selection", List.of(target))));
        assertEquals("working", git.text(root, "show", "HEAD:" + path));
        assertEquals(path, git.text(root, "ls-files")); assertTrue(git.text(root, "diff", "--cached", "--name-only").isEmpty());
        assertNotEquals(0, git.run(root, "cat-file", "-e", "HEAD:module/target/new-ignored.jar").code());
    }
    @Test void selectedRenameAndDeletionCommitAtomically() throws Exception {
        commitBase("base\n"); git.text(root, "mv", "file.txt", "renamed.txt");
        var target = new CommitTarget(repository.descriptor().id(), changes.status(repository, true).version(), List.of(new CommitFile("renamed.txt", "staged")));
        success(actions.commits(workspace, new SelectedCommit("Rename", List.of(target))));
        assertNotEquals(0, git.run(root, "cat-file", "-e", "HEAD:file.txt").code()); assertEquals("base", git.text(root, "show", "HEAD:renamed.txt"));
        Files.delete(root.resolve("renamed.txt"));
        target = new CommitTarget(repository.descriptor().id(), changes.status(repository, true).version(), List.of(new CommitFile("renamed.txt", "changes")));
        success(actions.commits(workspace, new SelectedCommit("Delete selected", List.of(target))));
        assertTrue(git.text(root, "ls-tree", "HEAD").isEmpty()); assertTrue(git.text(root, "ls-files").isEmpty());
    }
    @Test void selectedCommitBatchReportsHookFailureWithoutLosingOtherResults() throws Exception {
        commitBase("base\n"); write("file.txt", "first selected\n");
        Path second = Files.createDirectory(base.resolve("second")); git.text(second, "init", "-b", "main"); projects.add(second);
        git.text(second, "config", "user.name", "Fixture"); git.text(second, "config", "user.email", "fixture@example.invalid");
        Files.writeString(second.resolve("file.txt"), "second selected\n"); git.text(second, "add", "file.txt");
        Path hook = second.resolve(".git/hooks/pre-commit"); Files.writeString(hook, "#!/bin/sh\necho 'Fixture hook refused commit' >&2\nexit 1\n"); assertTrue(hook.toFile().setExecutable(true));
        var found = discovery.discover(workspace); String id = found.repositories().stream().filter(repo -> repo.name().equals("second")).findFirst().orElseThrow().id();
        var other = discovery.repository(workspace, id); String originalIndex = GitChangeService.fileHash(other.gitDir().resolve("index"));
        var firstTarget = new CommitTarget(repository.descriptor().id(), changes.status(repository, true).version(), List.of(new CommitFile("file.txt", "changes")));
        var secondTarget = new CommitTarget(id, changes.status(other, true).version(), List.of(new CommitFile("file.txt", "staged")));
        var result = await(actions.commits(workspace, new SelectedCommit("Batch selection", List.of(firstTarget, secondTarget))));
        assertEquals("success", result.outcomes().stream().filter(o -> o.repositoryId().equals(firstTarget.id())).findFirst().orElseThrow().state());
        assertEquals("failed", result.outcomes().stream().filter(o -> o.repositoryId().equals(id)).findFirst().orElseThrow().state());
        assertEquals("first selected", git.text(root, "show", "HEAD:file.txt")); assertEquals(originalIndex, GitChangeService.fileHash(other.gitDir().resolve("index")));
        assertEquals("unborn", changes.head(other)); assertFalse(Files.exists(other.gitDir().resolve("index.lock")));
    }
    @Test void selectedCommitRejectsStaleDuplicateAndUnfinishedOperationTargets() throws Exception {
        commitBase("base\n"); write("file.txt", "changed\n");
        var target = new CommitTarget(repository.descriptor().id(), changes.status(repository, true).version(), List.of(new CommitFile("file.txt", "changes")));
        write("file.txt", "external\n");
        assertThrows(GitProblem.class, () -> actions.commits(workspace, new SelectedCommit("Stale", List.of(target))));
        var duplicate = new CommitTarget(repository.descriptor().id(), changes.status(repository, true).version(), List.of(new CommitFile("file.txt", "changes"), new CommitFile("file.txt", "changes")));
        assertThrows(GitProblem.class, () -> actions.commits(workspace, new SelectedCommit("Duplicate", List.of(duplicate))));
        Files.writeString(repository.gitDir().resolve("CHERRY_PICK_HEAD"), changes.head(repository));
        var current = new CommitTarget(repository.descriptor().id(), changes.status(repository, true).version(), List.of(new CommitFile("file.txt", "changes")));
        assertThrows(GitProblem.class, () -> actions.commits(workspace, new SelectedCommit("In progress", List.of(current))));
        assertEquals("base\n", index("file.txt"));
    }
    @Test void selectedFilesCommitAndPushAcrossTwoRepositories() throws Exception {
        commitBase("base\n");
        Path second = Files.createDirectory(base.resolve("second")); git.text(second, "init", "-b", "main"); projects.add(second);
        git.text(second, "config", "user.name", "Fixture"); git.text(second, "config", "user.email", "fixture@example.invalid");
        Files.writeString(second.resolve("file.txt"), "second base\n"); git.text(second, "add", "file.txt"); git.text(second, "commit", "-m", "Second base");
        Path remote1 = base.resolve("remote1.git"), remote2 = base.resolve("remote2.git");
        git.text(base, "init", "--bare", remote1.toString()); git.text(base, "init", "--bare", remote2.toString());
        git.text(root, "remote", "add", "origin", remote1.toString()); git.text(root, "push", "-u", "origin", "main");
        git.text(second, "remote", "add", "origin", remote2.toString()); git.text(second, "push", "-u", "origin", "main");
        write("file.txt", "first selected\n"); write("new.txt", "first new selected\n"); write("excluded.txt", "first excluded\n"); git.text(root, "add", "excluded.txt");
        Files.writeString(second.resolve("file.txt"), "second staged\n"); git.text(second, "add", "file.txt"); Files.writeString(second.resolve("file.txt"), "second unstaged\n");
        var found = discovery.discover(workspace); String id = found.repositories().stream().filter(repo -> repo.name().equals("second")).findFirst().orElseThrow().id();
        var other = discovery.repository(workspace, id);
        var one = new CommitTarget(repository.descriptor().id(), changes.status(repository, true).version(), List.of(new CommitFile("file.txt", "changes"), new CommitFile("new.txt", "changes")));
        var two = new CommitTarget(id, changes.status(other, true).version(), List.of(new CommitFile("file.txt", "staged")));
        success(actions.commits(workspace, new SelectedCommit("Shared selected message", List.of(one, two))));
        assertEquals("Shared selected message", git.text(root, "log", "-1", "--format=%s")); assertEquals("Shared selected message", git.text(second, "log", "-1", "--format=%s"));
        success(actions.actions(workspace, new Action("push", List.of(new Target(one.id(), changes.status(repository, true).version()), new Target(id, changes.status(other, true).version())), null, false, null)));
        assertEquals("first selected", git.text(remote1, "show", "main:file.txt")); assertEquals("first new selected", git.text(remote1, "show", "main:new.txt"));
        assertNotEquals(0, git.run(remote1, "cat-file", "-e", "main:excluded.txt").code()); assertEquals("first excluded\n", index("excluded.txt"));
        assertEquals("second staged", git.text(remote2, "show", "main:file.txt")); assertEquals("second unstaged\n", Files.readString(second.resolve("file.txt")));
    }
    @Test void directoryReplacementCannotCommitAnUnselectedDeletion() throws Exception {
        commitBase("original\n"); Files.delete(root.resolve("file.txt")); git.text(root, "add", "-u");
        Files.createDirectory(root.resolve("file.txt")); Files.writeString(root.resolve("file.txt/child.txt"), "unrelated new content\n");
        String before = changes.head(repository); String indexBefore = GitChangeService.fileHash(repository.gitDir().resolve("index"));
        var target = new CommitTarget(repository.descriptor().id(), changes.status(repository, true).version(), List.of(new CommitFile("file.txt/child.txt", "changes")));
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
    @Test void destructiveActionsRejectSameSizeEditsWithRestoredModificationTime() throws Exception {
        commitBase("aaaa\n"); write("file.txt", "bbbb\n");
        var snapshot = new Target(repository.descriptor().id(), changes.status(repository, true).version());
        var modified = Files.getLastModifiedTime(root.resolve("file.txt"));
        write("file.txt", "cccc\n"); Files.setLastModifiedTime(root.resolve("file.txt"), modified);
        var result = await(actions.actions(workspace, new Action("discard", List.of(snapshot), null, false, "Fixture")));
        assertEquals("failed", result.outcomes().getFirst().state()); assertEquals("cccc\n", Files.readString(root.resolve("file.txt")));
        var listing = new Target(repository.descriptor().id(), changes.status(repository).version());
        assertEquals("failed", await(actions.actions(workspace, new Action("stash", List.of(listing), null, false, "confirmed"))).outcomes().getFirst().state(),
                "a listing version must never authorize a destructive action");
    }
    @Test void fullContextKeepsChangedLineIdentitiesAndStagesTheSameContent() throws Exception {
        StringBuilder base = new StringBuilder(); for (int i = 1; i <= 40; i++) base.append("line ").append(i).append('\n');
        commitBase(base.toString());
        String edited = base.toString().replace("line 3\n", "line three\n").replace("line 30\n", "line thirty\nadded\n");
        write("file.txt", edited);
        Diff normal = changes.diff(repository, "file.txt", "changes"); Diff full = changes.diff(repository, "file.txt", "changes", true);
        assertEquals(normal.version(), full.version());
        assertEquals(41, full.lines().stream().filter(line -> !line.type().equals("-")).count(), "every new-side line is present");
        var changedNormal = normal.lines().stream().filter(line -> !line.type().equals(" ")).map(line -> line.id() + "/" + line.block() + "/" + line.text()).toList();
        var changedFull = full.lines().stream().filter(line -> !line.type().equals(" ")).map(line -> line.id() + "/" + line.block() + "/" + line.text()).toList();
        assertEquals(changedNormal, changedFull);
        assertTrue(full.lines().stream().filter(line -> line.type().equals(" ")).allMatch(line -> line.block() == -1 && line.id().startsWith("c")));
        var fromFull = full.lines().stream().filter(line -> line.text().equals("line thirty") || line.text().equals("line 30")).map(Line::id).toList();
        changes.select(repository, new Selection("file.txt", "changes", full.version(), fromFull, false), true);
        assertEquals(base.toString().replace("line 30\n", "line thirty\n"), index("file.txt"));
    }
    Path remoteWithPeer() throws Exception {
        commitBase("base\n"); Path remote = base.resolve("remote.git");
        git.text(base, "init", "--bare", remote.toString()); git.text(root, "remote", "add", "origin", remote.toString()); git.text(root, "push", "-u", "origin", "main");
        Path peer = base.resolve("peer"); git.text(base, "clone", "-b", "main", remote.toString(), peer.toString());
        git.text(peer, "config", "user.name", "Fixture"); git.text(peer, "config", "user.email", "fixture@example.invalid");
        rediscover(); return peer;
    }
    @Test void commitAndPushReportsBothStepsAndNeverUndoesTheCommit() throws Exception {
        Path remote = remoteWithPeer().getParent().resolve("remote.git");
        write("file.txt", "pushed\n"); git.text(root, "add", "file.txt");
        var pushed = await(commitMarked("Pushed", true, new CommitFile("file.txt", "staged"))).outcomes().getFirst();
        String head = git.text(root, "rev-parse", "HEAD");
        assertEquals("success", pushed.state()); assertTrue(pushed.message().contains(head.substring(0, 10)) && pushed.message().contains("pushed"), pushed.message());
        assertEquals(head, git.text(remote, "rev-parse", "main"));
        Path peer = base.resolve("peer"); git.text(peer, "pull"); Files.writeString(peer.resolve("peer.txt"), "peer\n"); git.text(peer, "add", "peer.txt"); git.text(peer, "commit", "-m", "Peer"); git.text(peer, "push");
        write("file.txt", "rejected\n"); git.text(root, "add", "file.txt");
        var rejected = await(commitMarked("Rejected", true, new CommitFile("file.txt", "staged"))).outcomes().getFirst();
        String local = git.text(root, "rev-parse", "HEAD");
        assertEquals("committed", rejected.state()); assertTrue(rejected.message().contains(local.substring(0, 10)) && rejected.message().contains("push failed"), rejected.message());
        assertEquals("Rejected", git.text(root, "log", "-1", "--format=%s")); assertNotEquals(local, git.text(remote, "rev-parse", "main"));
    }
    @Test void commitAndPushWithoutUpstreamKeepsTheCommitAndSuggestsPublishing() throws Exception {
        commitBase("base\n"); write("file.txt", "local\n");
        var target = new CommitTarget(repository.descriptor().id(), changes.status(repository, true).version(), List.of(new CommitFile("file.txt", "changes")));
        var outcome = await(actions.commits(workspace, new SelectedCommit("Local only", List.of(target), true))).outcomes().getFirst();
        assertEquals("committed", outcome.state()); assertTrue(outcome.message().contains("Publish branch"), outcome.message());
        assertEquals("Local only", git.text(root, "log", "-1", "--format=%s"));
    }
    @Test void publishSetsTheUpstreamOnTheChosenRemoteAndRequiresARemote() throws Exception {
        commitBase("base\n");
        var none = await(actions.actions(workspace, new Action("publish", List.of(new Target(repository.descriptor().id(), null)), null, false, null, null))).outcomes().getFirst();
        assertEquals("failed", none.state()); assertTrue(none.message().contains("No remote"), none.message());
        Path remote = base.resolve("remote.git"); git.text(base, "init", "--bare", remote.toString()); git.text(root, "remote", "add", "origin", remote.toString());
        git.text(root, "checkout", "-b", "feature/new");
        assertEquals(List.of("origin"), actions.remotes(repository));
        var wrong = await(actions.actions(workspace, new Action("publish", List.of(new Target(repository.descriptor().id(), null)), null, false, null, "elsewhere"))).outcomes().getFirst();
        assertEquals("failed", wrong.state());
        success(actions.actions(workspace, new Action("publish", List.of(new Target(repository.descriptor().id(), null)), null, false, null, "origin")));
        var status = changes.status(repository);
        assertEquals("origin/feature/new", status.upstream()); assertEquals(0, status.ahead());
        assertEquals(git.text(root, "rev-parse", "HEAD"), git.text(remote, "rev-parse", "feature/new"));
    }
    @Test void confirmedRebasePullReplaysLocalCommitsAndStopsOnConflicts() throws Exception {
        Path peer = remoteWithPeer();
        Files.writeString(peer.resolve("peer.txt"), "peer\n"); git.text(peer, "add", "peer.txt"); git.text(peer, "commit", "-m", "Peer"); git.text(peer, "push");
        write("local.txt", "local\n"); git.text(root, "add", "local.txt"); git.text(root, "commit", "-m", "Local");
        success(actions.actions(workspace, action("fetch", null, false, null)));
        assertThrows(GitProblem.class, () -> actions.actions(workspace, action("pull-rebase", null, false, null)), "rebase requires confirmation");
        success(actions.actions(workspace, action("pull-rebase", null, false, "confirmed")));
        assertEquals("Local\nPeer\nBase", git.text(root, "log", "--format=%s"));
        Files.writeString(peer.resolve("file.txt"), "peer edit\n"); git.text(peer, "commit", "-am", "Peer edit"); git.text(peer, "pull", "--rebase"); git.text(peer, "push");
        write("file.txt", "local edit\n"); git.text(root, "commit", "-am", "Local edit");
        var conflict = await(actions.actions(workspace, action("pull-rebase", null, false, "confirmed"))).outcomes().getFirst();
        assertEquals("failed", conflict.state()); assertTrue(conflict.message().contains("rebase --continue"), conflict.message());
        assertTrue(Files.exists(repository.gitDir().resolve("rebase-merge")) || Files.exists(repository.gitDir().resolve("rebase-apply")));
    }
    Action delete(String path) { return new Action("delete-untracked", List.of(new Target(repository.descriptor().id(), null)), path, false, "confirmed"); }
    @Test void deletingUntrackedFilesRevalidatesAndNeverFollowsLinks() throws Exception {
        commitBase("base\n"); write("scratch.txt", "x\n");
        success(actions.actions(workspace, delete("scratch.txt"))); assertFalse(Files.exists(root.resolve("scratch.txt")));
        write("staged-later.txt", "x\n"); git.text(root, "add", "staged-later.txt");
        assertEquals("failed", await(actions.actions(workspace, delete("staged-later.txt"))).outcomes().getFirst().state());
        assertTrue(Files.exists(root.resolve("staged-later.txt")));
        assertEquals("failed", await(actions.actions(workspace, delete("file.txt"))).outcomes().getFirst().state(), "tracked files are never deleted");
        Path outside = Files.createDirectories(base.resolve("outside")); Files.writeString(outside.resolve("keep.txt"), "keep\n");
        write("generated/a.txt", "a\n"); Files.createSymbolicLink(root.resolve("generated/link"), outside);
        success(actions.actions(workspace, delete("generated/")));
        assertFalse(Files.exists(root.resolve("generated"))); assertTrue(Files.exists(outside.resolve("keep.txt")));
        write("cache/inner.txt", "x\n"); Files.writeString(root.resolve(".gitignore"), "cache/inner.txt\n");
        assertEquals("failed", await(actions.actions(workspace, delete("cache/inner.txt"))).outcomes().getFirst().state(), "ignored files are not untracked changes");
        assertThrows(GitProblem.class, () -> actions.actions(workspace, new Action("delete-untracked", List.of(new Target(repository.descriptor().id(), null)), "x", false, null)));
    }
    @Test void syncSummaryReportsEveryLocalBranchFromLocalRefs() throws Exception {
        Path peer = remoteWithPeer();
        for (String branch : List.of("feature", "behindonly", "synced")) { git.text(root, "branch", branch); git.text(root, "push", "-u", "origin", branch); }
        git.text(root, "branch", "loose");
        git.text(root, "checkout", "feature"); for (int i = 0; i < 2; i++) { write("f" + i, "x\n"); git.text(root, "add", "-A"); git.text(root, "commit", "-m", "Feature " + i); }
        git.text(root, "checkout", "main"); write("local.txt", "local\n"); git.text(root, "add", "local.txt"); git.text(root, "commit", "-m", "Local");
        git.text(peer, "fetch"); Files.writeString(peer.resolve("peer.txt"), "peer\n"); git.text(peer, "add", "peer.txt"); git.text(peer, "commit", "-m", "Peer"); git.text(peer, "push");
        git.text(peer, "checkout", "-b", "behindonly", "origin/behindonly"); Files.writeString(peer.resolve("b.txt"), "b\n"); git.text(peer, "add", "b.txt"); git.text(peer, "commit", "-m", "B"); git.text(peer, "push");
        git.text(root, "fetch"); write("pending.txt", "pending\n");
        var sync = new GitSyncService(discovery, changes, git);
        var summary = sync.summary(workspace).repositories().stream().filter(r -> r.name().equals("plain-repository")).findFirst().orElseThrow();
        Map<String, SyncBranch> branches = new HashMap<>(); summary.branches().forEach(branch -> branches.put(branch.name(), branch));
        assertEquals("main", summary.branch()); assertEquals(1, summary.changes()); assertNotNull(summary.lastFetch());
        // "behindonly" and "feature" sort before "main"; the current branch still comes first.
        assertEquals("main", summary.branches().getFirst().name()); assertEquals("plain-repository", summary.displayName()); assertEquals(root.toRealPath().toString(), summary.absolutePath());
        assertTrue(branches.get("main").current()); assertEquals(1, branches.get("main").ahead()); assertEquals(1, branches.get("main").behind());
        assertFalse(branches.get("feature").current()); assertEquals(2, branches.get("feature").ahead()); assertEquals(0, branches.get("feature").behind());
        assertEquals(0, branches.get("behindonly").ahead()); assertEquals(1, branches.get("behindonly").behind());
        assertNull(branches.get("loose").upstream()); assertNull(branches.get("loose").ahead());
        assertEquals(0, branches.get("synced").ahead()); assertEquals(0, branches.get("synced").behind());
    }
    @Test void historyListsCommitsFilesAndReadOnlyComparisons() throws Exception {
        var history = new GitHistoryService(git, changes);
        assertTrue(history.log(repository, 0, 30).commits().isEmpty());
        String lines = "1\n2\n3\n4\n5\n6\n7\n8\n9\n";
        write("a.txt", lines); git.text(root, "add", "-A"); git.text(root, "commit", "-m", "Root");
        String rootCommit = git.text(root, "rev-parse", "HEAD");
        var rootDiff = changes.commitDiff(repository, rootCommit, "a.txt", null);
        assertTrue(rootDiff.lines().stream().allMatch(line -> line.type().equals("+"))); assertEquals(9, rootDiff.lines().size()); assertFalse(rootDiff.partial());
        Path remote = base.resolve("remote.git"); git.text(base, "init", "--bare", remote.toString()); git.text(root, "remote", "add", "origin", remote.toString()); git.text(root, "push", "-u", "origin", "main");
        git.text(root, "mv", "a.txt", "b.txt"); write("b.txt", lines.replace("5\n", "five\n")); write("c.txt", "c\n"); git.text(root, "add", "-A"); git.text(root, "commit", "-m", "Second");
        var page = history.log(repository, 0, 1);
        assertEquals("Second", page.commits().getFirst().subject()); assertFalse(page.commits().getFirst().pushed()); assertTrue(page.more()); assertTrue(page.upstream());
        assertTrue(history.log(repository, 1, 1).commits().getFirst().pushed());
        var files = history.files(repository, page.commits().getFirst().sha());
        assertTrue(files.contains(new CommitFileChange("A", "c.txt", null)), files.toString());
        assertTrue(files.contains(new CommitFileChange("R", "b.txt", "a.txt")), files.toString());
        var renamed = changes.commitDiff(repository, page.commits().getFirst().sha(), "b.txt", "a.txt");
        assertEquals(List.of("-5", "+five"), renamed.lines().stream().filter(line -> !line.type().equals(" ")).map(line -> line.type() + line.text()).toList());
        assertThrows(GitProblem.class, () -> history.files(repository, "HEAD~1")); assertThrows(GitProblem.class, () -> history.files(repository, "--output=x"));
        assertThrows(GitProblem.class, () -> changes.commitDiff(repository, rootCommit, "../escape", null));
    }
    Action stashAction(String action, StashEntry entry, String confirmation) {
        return new Action(action, List.of(new Target(repository.descriptor().id(), null)), null, false, confirmation, null, entry.ref(), entry.sha());
    }
    @Test void stashesAreListedAndAppliedOnlyWhileTheSelectedStashIsUnchanged() throws Exception {
        commitBase("base\n"); assertTrue(actions.stashes(repository).isEmpty());
        write("file.txt", "first\n"); git.text(root, "stash", "push", "-m", "First idea");
        var first = actions.stashes(repository).getFirst();
        assertEquals("stash@{0}", first.ref()); assertEquals("First idea", first.message()); assertEquals("main", first.branch());
        success(actions.actions(workspace, stashAction("stash-pop", first, null)));
        assertEquals("first\n", Files.readString(root.resolve("file.txt"))); assertTrue(actions.stashes(repository).isEmpty());
        git.text(root, "stash", "push", "-m", "Conflicting");
        var conflicting = actions.stashes(repository).getFirst();
        write("file.txt", "other\n"); git.text(root, "commit", "-am", "Other");
        var applied = await(actions.actions(workspace, stashAction("stash-pop", conflicting, null))).outcomes().getFirst();
        assertEquals("failed", applied.state()); assertTrue(applied.message().contains("kept"), applied.message());
        assertEquals(1, actions.stashes(repository).size());
        git.text(root, "checkout", "--theirs", "--", "file.txt"); git.text(root, "reset", "--hard");
        write("file.txt", "newer\n"); git.text(root, "stash", "push", "-m", "Newer");
        var stale = await(actions.actions(workspace, stashAction("stash-drop", conflicting, "confirmed"))).outcomes().getFirst();
        assertEquals("failed", stale.state(), "stash@{0} now names a different stash");
        assertEquals(2, actions.stashes(repository).size());
        var current = actions.stashes(repository).get(1);
        assertThrows(GitProblem.class, () -> actions.actions(workspace, stashAction("stash-drop", current, null)), "drop requires confirmation");
        success(actions.actions(workspace, stashAction("stash-drop", current, "confirmed")));
        assertEquals(List.of("Newer"), actions.stashes(repository).stream().map(StashEntry::message).toList());
    }
    @Test void editingPreservesConventionsAndRejectsStaleOrUnsafeWrites() throws Exception {
        var files = new GitFileEditService(git, changes);
        write("crlf.txt", "a\r\nb\r\n"); git.text(root, "add", "crlf.txt"); git.text(root, "commit", "-m", "Base"); write("crlf.txt", "a\r\nb\r\nc\r\n");
        String indexBefore = GitChangeService.fileHash(repository.gitDir().resolve("index"));
        var opened = files.read(repository, "crlf.txt");
        assertTrue(opened.editable()); assertEquals("a\nb\nc\n", opened.content()); assertEquals("crlf", opened.eol());
        files.write(repository, "crlf.txt", "a\nB\nc\n", opened.hash());
        assertEquals("a\r\nB\r\nc\r\n", Files.readString(root.resolve("crlf.txt")));
        assertEquals(indexBefore, GitChangeService.fileHash(repository.gitDir().resolve("index")), "saving never stages");
        var reopened = files.read(repository, "crlf.txt");
        write("crlf.txt", "external\r\n");
        var stale = assertThrows(GitProblem.class, () -> files.write(repository, "crlf.txt", "mine\n", reopened.hash()));
        assertEquals(409, stale.status()); assertEquals("external\r\n", Files.readString(root.resolve("crlf.txt")));
        Files.write(root.resolve("bom.txt"), new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'x'});
        var bom = files.read(repository, "bom.txt"); assertTrue(bom.bom()); assertFalse(bom.finalNewline());
        files.write(repository, "bom.txt", "y", bom.hash());
        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'y'}, Files.readAllBytes(root.resolve("bom.txt")));
        Files.write(root.resolve("binary.bin"), new byte[]{1, 0, 2});
        assertFalse(files.read(repository, "binary.bin").editable());
        assertEquals(403, assertThrows(GitProblem.class, () -> files.read(repository, "../outside.txt")).status());
        assertEquals(403, assertThrows(GitProblem.class, () -> files.write(repository, ".git/config", "x", "h")).status());
        assertFalse(files.read(repository, "file.txt").editable(), "unchanged files are not listed under Changes");
        try (var listing = Files.list(root)) { assertTrue(listing.noneMatch(path -> path.getFileName().toString().contains(".mvnbuilder-")), "temporary files are removed"); }
    }
}
