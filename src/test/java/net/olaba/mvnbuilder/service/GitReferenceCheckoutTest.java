package net.olaba.mvnbuilder.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real repositories ensure labels, symbolic HEAD, commit identity and upstream agree. */
class GitReferenceCheckoutTest {
    @TempDir Path directory;
    private final GitService service = new GitService();
    private String taggedCommit;
    private String remoteCommit;
    private String mainCommit;

    @BeforeEach
    void createRepository() throws Exception {
        git("init", "-b", "main");
        git("config", "user.name", "Reference Tests");
        git("config", "user.email", "reference-tests@invalid.example");
        git("config", "commit.gpgsign", "false");
        git("config", "tag.gpgsign", "false");
        git("config", "core.hooksPath", directory.resolve("disabled-hooks").toString());
        taggedCommit = commit("tagged");
        git("tag", "v1.0.0");
        remoteCommit = commit("remote");
        git("remote", "add", "origin", ".");
        git("update-ref", "refs/remotes/origin/feature/demo", remoteCommit);
        mainCommit = commit("main");
    }

    @Test
    void attachedBranchTakesPrecedenceOverTags() throws Exception {
        git("tag", "v9.0.0");
        final var current = service.getCurrentReference(directory.toFile());
        assertEquals("main", current.getLabel());
        assertEquals("refs/heads/main", current.localRef());
        assertTrue(current.tags().isEmpty());
    }

    @Test
    void exactTagsAndDetachedIdentitySurviveRepeatedQueries() throws Exception {
        git("tag", "-a", "v2.0.0", "-m", "annotated", taggedCommit);
        final var current = checkout("refs/tags/v1.0.0", "TAG");
        assertEquals(taggedCommit, git("rev-parse", "HEAD"));
        assertEquals(1, result("symbolic-ref", "--quiet", "HEAD").code());
        assertEquals(List.of("refs/tags/v2.0.0", "refs/tags/v1.0.0"), current.tags());
        assertEquals("v2.0.0 · detached HEAD", current.getLabel());
        assertEquals(current.getLabel(), service.getCurrentBranch(directory.toFile()));
        for (final var tag : service.listReferences(directory.toFile(), GitService.ReferenceType.TAG)) {
            assertTrue(current.isActive(tag));
        }
        checkout("refs/tags/v2.0.0", "TAG");
        assertEquals(taggedCommit, git("rev-parse", "HEAD"));
    }

    @Test
    void untaggedDetachedCommitDoesNotUseNearestAncestorTag() throws Exception {
        git("checkout", "--detach", mainCommit);
        final var current = service.getCurrentReference(directory.toFile());
        assertTrue(current.tags().isEmpty());
        assertEquals(mainCommit.substring(0, 12) + " · detached HEAD", current.getLabel());
        git("checkout", "main");
        assertEquals("main", service.getCurrentBranch(directory.toFile()));
    }

    @Test
    void sameNamedBranchAndTagCannotSelectEachOther() throws Exception {
        git("tag", "release", taggedCommit);
        git("branch", "release", mainCommit);
        checkout("refs/tags/release", "TAG");
        assertEquals(taggedCommit, git("rev-parse", "HEAD"));
        assertEquals(1, result("symbolic-ref", "--quiet", "HEAD").code());
        final var local = checkout("refs/heads/release", "LOCAL");
        assertEquals("release", local.getLabel());
        assertEquals(mainCommit, git("rev-parse", "HEAD"));
        assertEquals("release", checkout("release", null).getLabel());
    }

    @Test
    void remoteCreatesAttachedTrackingBranchDespiteSimilarLocalName() throws Exception {
        git("branch", "origin/feature/demo", mainCommit);
        final var current = checkout("refs/remotes/origin/feature/demo", "REMOTE");
        assertEquals("feature/demo", current.getLabel());
        assertEquals("refs/heads/feature/demo", git("symbolic-ref", "HEAD"));
        assertEquals(remoteCommit, git("rev-parse", "HEAD"));
        assertEquals("refs/remotes/origin/feature/demo", current.upstreamRef());
        assertEquals("refs/remotes/origin/feature/demo", git("rev-parse", "--symbolic-full-name", "@{upstream}"));
        assertEquals(mainCommit, git("rev-parse", "refs/heads/origin/feature/demo"));
        assertTrue(current.isActive(service.listReferences(directory.toFile(), GitService.ReferenceType.REMOTE).getFirst()));
    }

    @Test
    void remoteReusePreservesDivergentLocalHistory() throws Exception {
        checkout("refs/remotes/origin/feature/demo", "REMOTE");
        final String localTip = commit("local-extra");
        git("checkout", "main");
        // The remote advances independently along main: neither tip contains the other.
        git("update-ref", "refs/remotes/origin/feature/demo", mainCommit);
        final var current = checkout("refs/remotes/origin/feature/demo", "REMOTE");
        assertEquals(localTip, current.commit());
        assertEquals("feature/demo", current.getLabel());
        assertEquals(mainCommit, git("rev-parse", "@{upstream}"));
    }

    @Test
    void missingAndDifferentUpstreamsAreConflictsWithoutMutation() throws Exception {
        git("branch", "feature/demo", taggedCommit);
        assertThrows(GitService.ReferenceConflictException.class,
                () -> checkout("refs/remotes/origin/feature/demo", "REMOTE"));
        assertEquals("main", git("branch", "--show-current"));
        assertEquals(taggedCommit, git("rev-parse", "refs/heads/feature/demo"));
        assertEquals("", git("for-each-ref", "--format=%(upstream)", "refs/heads/feature/demo"));
        git("update-ref", "refs/remotes/origin/other", remoteCommit);
        git("branch", "--set-upstream-to=origin/other", "feature/demo");
        assertThrows(GitService.ReferenceConflictException.class,
                () -> checkout("refs/remotes/origin/feature/demo", "REMOTE"));
        assertEquals("main", git("branch", "--show-current"));
        assertEquals("refs/remotes/origin/other",
                git("for-each-ref", "--format=%(upstream)", "refs/heads/feature/demo"));
    }

    @Test
    void remoteNamesAndNestedPathsComeFromConfiguredRemotes() throws Exception {
        git("remote", "add", "upstream", ".");
        git("update-ref", "refs/remotes/upstream/team/feature/demo", remoteCommit);
        final var current = checkout("refs/remotes/upstream/team/feature/demo", "REMOTE");
        assertEquals("team/feature/demo", current.getLabel());
        assertEquals("refs/remotes/upstream/team/feature/demo", current.upstreamRef());
        assertEquals(remoteCommit, current.commit());
    }

    @Test
    void listingsKeepFullIdentityAndExcludeSymbolicRemoteHead() throws Exception {
        git("symbolic-ref", "refs/remotes/origin/HEAD", "refs/remotes/origin/feature/demo");
        git("branch", "v1.0.0");
        final var remotes = service.listReferences(directory.toFile(), GitService.ReferenceType.REMOTE);
        assertEquals(1, remotes.size());
        assertEquals("refs/remotes/origin/feature/demo", remotes.getFirst().ref());
        assertEquals("feature/demo", remotes.getFirst().localBranch());
        assertEquals(List.of("origin/feature/demo"), service.listRemoteBranches(directory.toFile()));
        assertTrue(service.listBranches(directory.toFile()).contains("v1.0.0"));
        assertEquals(List.of("v1.0.0"), service.listTags(directory.toFile()));
    }

    @Test
    void invalidRequestsNeverChangeHeadOrGuessARemote() throws Exception {
        git("symbolic-ref", "refs/remotes/origin/alias", "refs/remotes/origin/feature/demo");
        assertThrows(IllegalArgumentException.class, () -> checkout("refs/tags/v1.0.0", "UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> checkout("refs/tags/v1.0.0", "LOCAL"));
        assertThrows(IllegalArgumentException.class, () -> checkout("refs/tags/missing", "TAG"));
        assertThrows(IllegalArgumentException.class, () -> checkout("refs/remotes/origin/HEAD", "REMOTE"));
        assertThrows(IllegalArgumentException.class, () -> checkout("refs/remotes/origin/alias", "REMOTE"));
        assertThrows(IllegalArgumentException.class, () -> checkout("feature/demo", null));
        assertThrows(IllegalArgumentException.class, () -> checkout("--orphan=wrong", null));
        assertThrows(IllegalArgumentException.class, () -> checkout("version.txt", null));
        assertThrows(IllegalArgumentException.class, () -> checkout("", "TAG"));
        assertEquals(mainCommit, git("rev-parse", "HEAD"));
        assertEquals("main", git("branch", "--show-current"));
        assertEquals(1, result("show-ref", "--verify", "--quiet", "refs/heads/feature/demo").code());
    }

    @Test
    void dirtyFilesArePreservedWhenGitRefusesRemoteOrTagCheckout() throws Exception {
        Files.writeString(directory.resolve("version.txt"), "uncommitted");
        assertThrows(IllegalStateException.class, () -> checkout("refs/remotes/origin/feature/demo", "REMOTE"));
        assertEquals(1, result("show-ref", "--verify", "--quiet", "refs/heads/feature/demo").code());
        assertThrows(IllegalStateException.class, () -> checkout("refs/tags/v1.0.0", "TAG"));
        assertEquals("uncommitted", Files.readString(directory.resolve("version.txt")));
        assertEquals("refs/heads/main", git("symbolic-ref", "HEAD"));
        assertEquals(mainCommit, git("rev-parse", "HEAD"));
    }

    @Test
    void longTagLabelsFitCacheAndRetainDetachedMarkerAndFullIdentity() throws Exception {
        final String name = "v" + "x".repeat(120) + "/" + "y".repeat(120) + "/" + "z".repeat(120);
        git("tag", name, remoteCommit);
        final var current = checkout("refs/tags/" + name, "TAG");
        assertEquals(255, current.getLabel().length());
        assertTrue(current.getLabel().endsWith("… · detached HEAD"));
        assertEquals(name + " · detached HEAD", current.getDescription());
        assertEquals("refs/tags/" + name, current.tags().getFirst());
    }

    @Test
    void unavailableRepositoryRetainsUnavailableLabel() throws Exception {
        // A child of a repository is itself in that repository; use a separate sibling.
        final Path outside = Files.createTempDirectory(directory.getParent(), "unavailable-git-");
        try {
            assertEquals("n/a", service.getCurrentBranch(outside.toFile()));
            assertTrue(service.listBranches(outside.toFile()).isEmpty());
        } finally {
            Files.delete(outside);
        }
    }

    @Test
    void branchLabelsComeFromTheLightweightLookup() throws Exception {
        assertEquals("main", service.getCurrentBranch(directory.toFile()));
        git("checkout", "--detach", taggedCommit);
        assertEquals("v1.0.0 · detached HEAD", service.getCurrentBranch(directory.toFile()));
    }

    @Test
    void unbornBranchShowsItsName() throws Exception {
        final Path fresh = Files.createTempDirectory(directory.getParent(), "unborn-git-");
        try {
            final var builder = new ProcessBuilder("git", "init", "-b", "main").directory(fresh.toFile());
            assertEquals(0, builder.start().waitFor());
            assertEquals("main", service.getCurrentBranch(fresh.toFile()));
        } finally {
            org.springframework.util.FileSystemUtils.deleteRecursively(fresh);
        }
    }

    @Test
    void longBranchLabelsAreTruncatedAsBefore() throws Exception {
        final String name = "b" + "x".repeat(120) + "/" + "y".repeat(120) + "/" + "z".repeat(120);
        git("checkout", "-b", name);
        final String label = service.getCurrentBranch(directory.toFile());
        assertEquals(255, label.length());
        assertEquals(service.getCurrentReference(directory.toFile()).getLabel(), label);
        assertTrue(label.endsWith("…"));
    }

    @Test
    void branchLookupStartsOneGitProcessUnlessDetached() throws Exception {
        final var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(GitService.class);
        final var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        final var previous = logger.getLevel();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        try {
            service.getCurrentBranch(directory.toFile());
            assertEquals(1, gitProcesses(appender));
            appender.list.clear();
            git("checkout", "--detach", taggedCommit);
            service.getCurrentBranch(directory.toFile());
            assertTrue(gitProcesses(appender) > 1);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
        }
    }

    private static long gitProcesses(final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender) {
        return appender.list.stream().filter(event -> event.getFormattedMessage().startsWith("Executing [git")).count();
    }

    private GitService.ReferenceState checkout(final String ref, final String type) {
        return service.checkoutReference(directory.toFile(), ref, type);
    }

    private String commit(final String content) throws Exception {
        Files.writeString(directory.resolve("version.txt"), content);
        git("add", "version.txt");
        git("commit", "-m", content);
        return git("rev-parse", "HEAD");
    }

    private record Result(int code, String output) { }

    private Result result(final String... args) throws Exception {
        final var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));
        final var builder = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        final Process process = builder.start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Git fixture command timed out");
        return new Result(process.exitValue(), new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip());
    }

    private String git(final String... args) throws Exception {
        final Result result = result(args);
        assertEquals(0, result.code(), () -> String.join(" ", args) + ": " + result.output());
        return result.output();
    }
}
