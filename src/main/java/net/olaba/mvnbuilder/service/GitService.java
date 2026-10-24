package net.olaba.mvnbuilder.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Git commands and reference identities for repositories on the file system. */
@Service
@Slf4j
public class GitService {

    public enum ReferenceType {
        LOCAL("refs/heads/"), REMOTE("refs/remotes/"), TAG("refs/tags/");

        private final String prefix;

        ReferenceType(final String prefix) {
            this.prefix = prefix;
        }
    }

    /** The ref is machine identity; name and localBranch are presentation data. */
    public record ReferenceChoice(ReferenceType type, String ref, String name, String localBranch) {
        public ReferenceType getType() { return type; }
        public String getRef() { return ref; }
        public String getName() { return name; }
        public String getLocalBranch() { return localBranch; }
    }

    /** Live state, independent of the cached label or the last clicked reference. */
    public record ReferenceState(String localRef, String upstreamRef, List<String> tags, String commit) {
        private static final String DETACHED = " · detached HEAD";
        private static final int CACHE_LIMIT = 255;

        public ReferenceState {
            tags = List.copyOf(tags);
        }

        public String getDescription() {
            if (localRef != null) return localRef.substring(ReferenceType.LOCAL.prefix.length());
            final String identity = tags.isEmpty() ? commit.substring(0, Math.min(12, commit.length()))
                    : tags.getFirst().substring(ReferenceType.TAG.prefix.length());
            return identity + DETACHED;
        }

        public String getLabel() {
            final String description = getDescription();
            if (description.length() <= CACHE_LIMIT) return description;
            final String suffix = localRef == null ? DETACHED : "";
            return description.substring(0, CACHE_LIMIT - suffix.length() - 1) + "…" + suffix;
        }

        public boolean isActive(final ReferenceChoice choice) {
            return switch (choice.type()) {
                case LOCAL -> choice.ref().equals(localRef);
                case REMOTE -> localRef != null && choice.ref().equals(upstreamRef);
                case TAG -> localRef == null && tags.contains(choice.ref());
            };
        }
    }

    /** A local name already exists but does not track the chosen remote branch. */
    public static class ReferenceConflictException extends IllegalStateException {
        public ReferenceConflictException(final String message) { super(message); }
    }

    /** Returns a cache-compatible label, including exact tags or detached commit identity. */
    public String getCurrentBranch(final File projectDir) {
        try {
            return getCurrentReference(projectDir).getLabel();
        } catch (final RuntimeException e) {
            log.warn("Failed to get Git reference for {}: {}", projectDir, e.getMessage());
            return "n/a";
        }
    }

    /** Resolves actual state; callers handling a checkout must not silently cache a failure. */
    public ReferenceState getCurrentReference(final File projectDir) {
        final String commit = readGit(projectDir, "rev-parse", "--verify", "HEAD^{commit}");
        final GitResult symbolic = runGit(projectDir, "symbolic-ref", "--quiet", "HEAD");
        if (symbolic.exitCode() == 0) {
            final String localRef = symbolic.output();
            final String upstream = readGit(projectDir, "for-each-ref", "--format=%(upstream)", localRef);
            return new ReferenceState(localRef, upstream, List.of(), commit);
        }
        if (symbolic.exitCode() != 1) throw new IllegalStateException(symbolic.message());
        final List<String> tags = lines(readGit(projectDir, "tag", "--points-at", commit,
                "--sort=-v:refname", "--format=%(refname)"));
        return new ReferenceState(null, null, tags, commit);
    }

    /** Lists exact refs; symbolic remote HEAD entries are never selectable. */
    public List<ReferenceChoice> listReferences(final File projectDir, final ReferenceType type) {
        try {
            final String output = readGit(projectDir, "for-each-ref",
                    type == ReferenceType.TAG ? "--sort=-v:refname" : "--sort=refname",
                    "--format=%(refname)%09%(symref)", type.prefix);
            final List<String> remotes = type == ReferenceType.REMOTE ? remoteNames(projectDir) : List.of();
            final List<ReferenceChoice> choices = new ArrayList<>();
            for (final String line : output.lines().toList()) {
                final String[] fields = line.split("\t", -1);
                if (fields.length != 2 || !fields[1].isEmpty()) continue;
                final String ref = fields[0];
                final String name = ref.substring(type.prefix.length());
                final String localName = type == ReferenceType.REMOTE
                        ? localNameForRemote(name, remotes) : name;
                choices.add(new ReferenceChoice(type, ref, name, localName));
            }
            return choices;
        } catch (final RuntimeException e) {
            log.warn("Failed to list Git {} references for {}: {}", type, projectDir, e.getMessage());
            return List.of();
        }
    }

    public List<String> listBranches(final File projectDir) {
        return listReferences(projectDir, ReferenceType.LOCAL).stream().map(ReferenceChoice::name).toList();
    }

    public List<String> listRemoteBranches(final File projectDir) {
        return listReferences(projectDir, ReferenceType.REMOTE).stream().map(ReferenceChoice::name).toList();
    }

    public List<String> listTags(final File projectDir) {
        return listReferences(projectDir, ReferenceType.TAG).stream().map(ReferenceChoice::name).toList();
    }

    /** Typed single-project checkout. A legacy request without a type is local-only. */
    public ReferenceState checkoutReference(final File projectDir, final String reference,
            final String referenceType) {
        return net.olaba.mvnbuilder.service.git.GitMutationCoordinator.mutate(projectDir,
                () -> checkoutReferenceCoordinated(projectDir, reference, referenceType));
    }

    private ReferenceState checkoutReferenceCoordinated(final File projectDir, final String reference,
            final String referenceType) {
        final ReferenceType type;
        try {
            type = referenceType == null ? ReferenceType.LOCAL : ReferenceType.valueOf(referenceType);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown Git reference type: " + referenceType);
        }
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Select a Git reference.");
        }
        final String ref = referenceType == null && !reference.startsWith("refs/")
                ? ReferenceType.LOCAL.prefix + reference : reference;
        if (!ref.startsWith(type.prefix) || runGit(projectDir, "check-ref-format", ref).exitCode() != 0) {
            throw new IllegalArgumentException("The selected reference does not match its Git reference type.");
        }
        if (runGit(projectDir, "show-ref", "--verify", "--quiet", ref).exitCode() != 0) {
            throw new IllegalArgumentException("The selected Git reference no longer exists: " + reference);
        }
        if (type == ReferenceType.REMOTE && runGit(projectDir, "symbolic-ref", "--quiet", ref).exitCode() == 0) {
            throw new IllegalArgumentException("Select a remote branch rather than a symbolic remote reference.");
        }
        // Validate that a tag/reference actually resolves to a commit before changing anything.
        final String commit = readGit(projectDir, "rev-parse", "--verify", ref + "^{commit}");
        switch (type) {
            case LOCAL -> activateLocal(projectDir, ref.substring(type.prefix.length()));
            case TAG -> executeGitCommand(projectDir, "checkout", "--detach", commit, "--");
            case REMOTE -> checkoutRemote(projectDir, ref);
        }
        return getCurrentReference(projectDir);
    }

    private void checkoutRemote(final File projectDir, final String ref) {
        final String name = ref.substring(ReferenceType.REMOTE.prefix.length());
        final String localName = localNameForRemote(name, remoteNames(projectDir));
        if (localName.isEmpty()) {
            throw new IllegalArgumentException("No configured remote matches " + name + ". Fetch references again.");
        }
        validateLocalName(projectDir, localName);
        final String localRef = ReferenceType.LOCAL.prefix + localName;
        if (runGit(projectDir, "show-ref", "--verify", "--quiet", localRef).exitCode() == 0) {
            final String upstream = readGit(projectDir, "for-each-ref", "--format=%(upstream)", localRef);
            if (!ref.equals(upstream)) {
                throw new ReferenceConflictException("Local branch '" + localName
                        + "' already exists and does not track '" + name
                        + "'. Select it in Local or configure its upstream explicitly.");
            }
            activateLocal(projectDir, localName);
        } else {
            executeGitCommand(projectDir, "checkout", "--track", "-b", localName, ref, "--");
        }
    }

    private void activateLocal(final File projectDir, final String name) {
        validateLocalName(projectDir, name);
        executeGitCommand(projectDir, "checkout", "--no-guess", name, "--");
    }

    private void validateLocalName(final File projectDir, final String name) {
        if (name.startsWith("-") || runGit(projectDir, "check-ref-format", "--branch", name).exitCode() != 0) {
            throw new IllegalArgumentException("The reference cannot be activated as a local branch: " + name);
        }
    }

    private List<String> remoteNames(final File projectDir) {
        return lines(readGit(projectDir, "remote")).stream()
                .sorted(Comparator.comparingInt(String::length).reversed()).toList();
    }

    private String localNameForRemote(final String name, final List<String> remotes) {
        return remotes.stream().filter(remote -> name.startsWith(remote + "/"))
                .findFirst().map(remote -> name.substring(remote.length() + 1)).orElse("");
    }

    /** Legacy bulk checkout retains its existing command contract. */
    public boolean checkoutBranch(final File projectDir, final String branchName) {
        return executeGitCommand(projectDir, "checkout", branchName);
    }

    public boolean createBranch(final File projectDir, final String branchName) {
        return executeGitCommand(projectDir, "checkout", "-b", branchName);
    }

    public boolean fetch(final File projectDir) {
        return executeGitCommand(projectDir, "fetch");
    }

    private List<String> lines(final String output) {
        return output.lines().filter(line -> !line.isEmpty()).toList();
    }

    private boolean executeGitCommand(final File projectDir, final String... args) {
        return net.olaba.mvnbuilder.service.git.GitMutationCoordinator.mutate(projectDir, () -> {
            readGit(projectDir, args);
            return true;
        });
    }

    private String readGit(final File projectDir, final String... args) {
        final GitResult result = runGit(projectDir, args);
        if (result.exitCode() != 0) throw new IllegalStateException(result.message());
        return result.output();
    }

    private record GitResult(int exitCode, String output) {
        String message() {
            return output.isEmpty() ? "Git command failed with exit code " + exitCode : output;
        }
    }

    /** Drain output concurrently so Git cannot block on a full pipe; bound command duration. */
    private GitResult runGit(final File projectDir, final String... args) {
        final List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = null;
        try {
            log.debug("Executing {} in {}", command, projectDir);
            final ProcessBuilder builder = new ProcessBuilder(command).directory(projectDir).redirectErrorStream(true);
            builder.environment().put("GIT_TERMINAL_PROMPT", "0");
            process = builder.start();
            final Process running = process;
            final CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
                try (var reader = running.inputReader(StandardCharsets.UTF_8)) {
                    return reader.lines().collect(Collectors.joining("\n"));
                } catch (final java.io.IOException e) {
                    throw new IllegalStateException("Cannot read Git output", e);
                }
            });
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Git command timed out: " + String.join(" ", command));
            }
            // Do not trim: trailing tabs carry reference-listing metadata.
            return new GitResult(process.exitValue(), output.get(2, TimeUnit.SECONDS));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Git command interrupted", e);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to execute Git: " + e.getMessage(), e);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }
}
