package net.olaba.mvnbuilder.service.git;

import static net.olaba.mvnbuilder.service.git.GitModels.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Selected-file commits use a private index; unrelated staging is never committed or reset. */
public final class GitSelectedCommitService {
    private final GitCommandRunner git;
    private final GitChangeService changes;
    public GitSelectedCommitService(GitCommandRunner git, GitChangeService changes) { this.git = git; this.changes = changes; }
    private record Planned(String path, String area, List<String> paths) { }
    private List<Planned> plan(Resolved repository, CommitTarget target) {
        if (target.files() == null || target.files().isEmpty() || target.files().size() > 4096)
            throw new GitProblem(400, "Select between 1 and 4096 files for each repository.");
        changes.expect(repository, target.version());
        for (String marker : List.of("MERGE_HEAD", "CHERRY_PICK_HEAD", "REVERT_HEAD", "rebase-merge", "rebase-apply"))
            if (Files.exists(repository.gitDir().resolve(marker))) throw new GitProblem(422, "Finish the current merge, rebase, cherry-pick, or revert before committing selected files. Use the normal staged commit where appropriate.");
        Status status = changes.status(repository);
        if (status.files().stream().anyMatch(file -> file.kind().equals("conflict"))) throw new GitProblem(422, "Resolve all conflicts before committing selected files.");
        List<Planned> plan = new ArrayList<>(); Set<String> paths = new HashSet<>();
        for (CommitFile selected : target.files()) {
            if (selected == null || selected.area() == null || !Set.of("changes", "staged").contains(selected.area())) throw new GitProblem(400, "Choose Changes or Staged Changes for each selected file.");
            WorkspaceGitDiscoveryService.safePath(repository, selected.path());
            FileChange change = GitChangeService.find(status, selected.path(), selected.area());
            if (change.kind().equals("gitlink")) throw new GitProblem(422, "Commit submodule changes from their own repository.");
            List<String> pair = new ArrayList<>(change.kind().equals("untracked-dir") ? untrackedFiles(repository, selected.path()) : List.of(selected.path()));
            if (pair.isEmpty()) throw GitProblem.stale();
            if (change.oldPath() != null) { WorkspaceGitDiscoveryService.safePath(repository, change.oldPath()); pair.add(change.oldPath()); }
            for (String path : pair) if (!paths.add(path)) throw new GitProblem(400, "A file can be selected from only one source. Review duplicate or overlapping rename selections.");
            plan.add(new Planned(selected.path(), selected.area(), List.copyOf(pair)));
        }
        return List.copyOf(plan);
    }
    /** A selected untracked folder contributes exactly the non-ignored files Git reports inside it. */
    private List<String> untrackedFiles(Resolved repository, String directory) {
        List<String> files = new ArrayList<>();
        for (String path : git.run(repository.root(), "ls-files", "--others", "--exclude-standard", "-z", "--", directory).require().text().split("\0"))
            if (!path.isEmpty()) { WorkspaceGitDiscoveryService.safePath(repository, path); files.add(path); }
        return files;
    }
    public void validate(Resolved repository, CommitTarget target) { plan(repository, target); }
    public void commit(Resolved repository, CommitTarget target, String message) {
        GitMutationCoordinator.mutate(repository.commonDir(), () -> { commitLocked(repository, target, message); return null; });
    }
    private void commitLocked(Resolved repository, CommitTarget target, String message) {
        List<Planned> plan = plan(repository, target);
        String before = changes.head(repository);
        Path index = repository.gitDir().resolve("index"), lock = repository.gitDir().resolve("index.lock");
        String suffix = UUID.randomUUID().toString();
        Path candidate = repository.gitDir().resolve("mvnbuilder-commit-" + suffix);
        Path preserved = repository.gitDir().resolve("mvnbuilder-preserved-" + suffix);
        boolean ownsLock = false, commitStarted = false, committed = false;
        try {
            Files.createFile(lock); ownsLock = true;
            changes.expect(repository, target.version());
            Map<String, String> candidateEnv = Map.of("GIT_INDEX_FILE", candidate.toString());
            Map<String, String> preservedEnv = Map.of("GIT_INDEX_FILE", preserved.toString());
            if (Files.exists(index)) Files.copy(index, preserved);
            else git.run(repository.root(), null, preservedEnv, false, "read-tree", "--empty").require();
            git.run(repository.root(), null, candidateEnv, false, "read-tree", before.equals("unborn") ? "--empty" : before).require();
            Set<String> selectedPaths = new LinkedHashSet<>();
            for (Planned selected : plan) {
                selectedPaths.addAll(selected.paths());
                if (selected.area().equals("changes")) {
                    changes.stageWholeFiles(repository, candidateEnv, selected.paths());
                } else for (String path : selected.paths()) copyEntry(repository, Map.of(), candidateEnv, path);
            }
            var diff = git.run(repository.root(), null, candidateEnv, false, "diff", "--cached", "--quiet", "--no-ext-diff", "--no-textconv");
            if (diff.code() == 0) throw new GitProblem(422, "Selected files contain no changes to commit.");
            if (diff.code() != 1) diff.require();
            for (String path : git.run(repository.root(), null, candidateEnv, false,
                    "diff", "--cached", "--name-only", "--no-renames", "--no-ext-diff", "--no-textconv", "-z").require().text().split("\0"))
                if (!path.isEmpty() && !selectedPaths.contains(path))
                    throw new GitProblem(422, "The selected files would also change " + path + ". Include that file explicitly before committing this directory/file replacement.");
            changes.expect(repository, target.version());
            commitStarted = true;
            git.run(repository.root(), message.getBytes(StandardCharsets.UTF_8), candidateEnv, true, "commit", "--file=-").require();
            committed = true;
            String after = changes.head(repository);
            // Hooks can update their private index. Only selected paths may differ from the old HEAD.
            List<String> command = before.equals("unborn")
                    ? List.of("diff-tree", "--root", "--no-commit-id", "--name-only", "--no-renames", "-r", "-z", after)
                    : List.of("diff-tree", "--no-commit-id", "--name-only", "--no-renames", "-r", "-z", before, after);
            for (String path : git.run(repository.root(), command.toArray(String[]::new)).require().text().split("\0"))
                if (!path.isEmpty() && !selectedPaths.contains(path)) throw new GitProblem(503, "A commit hook added files outside the selection. The commit exists; inspect it before continuing. Original staging is preserved.");
            // Reconcile from the created commit, including modifications made by native hooks to selected paths.
            for (String path : selectedPaths) {
                git.run(repository.root(), null, preservedEnv, false, "update-index", "--force-remove", "--", path).require();
                var entry = git.run(repository.root(), "ls-tree", "-z", after, "--", path).require().text();
                if (!entry.isEmpty()) {
                    int tab = entry.indexOf('\t'); String[] metadata = entry.substring(0, tab).split(" ");
                    byte[] indexInfo = (metadata[0] + " " + metadata[2] + "\t" + path + "\0").getBytes(StandardCharsets.UTF_8);
                    git.run(repository.root(), indexInfo, preservedEnv, false, "update-index", "-z", "--index-info").require();
                }
            }
            if (!changes.head(repository).equals(after)) throw new GitProblem(503, "HEAD changed after committing. Inspect the repository; original staging is preserved.");
            Files.copy(preserved, lock, StandardCopyOption.REPLACE_EXISTING);
            Files.move(lock, index, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); ownsLock = false;
        } catch (FileAlreadyExistsException e) { throw new GitProblem(409, "Another Git operation holds index.lock. Wait and refresh before committing.");
        } catch (Exception e) {
            if (committed || commitStarted && !before.equals(changes.head(repository)))
                throw new GitProblem(503, "The selected-file commit may already exist. Refresh and inspect Git history before retrying. " + e.getMessage());
            if (e instanceof GitProblem problem) throw problem;
            throw new GitProblem(422, "Cannot commit selected files: " + e.getMessage());
        } finally {
            for (Path temporary : List.of(candidate, preserved, Path.of(candidate + ".lock"), Path.of(preserved + ".lock")))
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            if (ownsLock) try { Files.deleteIfExists(lock); } catch (IOException ignored) { }
        }
    }
    private void copyEntry(Resolved repository, Map<String, String> sourceEnv, Map<String, String> targetEnv, String path) {
        byte[] entry = git.run(repository.root(), null, sourceEnv, false, "ls-files", "--stage", "-z", "--", path).require().output();
        git.run(repository.root(), null, targetEnv, false, "update-index", "--force-remove", "--", path).require();
        if (entry.length != 0) git.run(repository.root(), entry, targetEnv, false, "update-index", "-z", "--index-info").require();
    }
}
