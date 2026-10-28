package net.olaba.mvnbuilder.service.git;

import jakarta.annotation.PreDestroy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import net.olaba.mvnbuilder.entities.Workspace;
import static net.olaba.mvnbuilder.service.git.GitModels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Service
public class GitRepositoryActionService {
    private final WorkspaceGitDiscoveryService discovery;
    private final GitChangeService changes;
    private final GitCommandRunner git;
    private final ExecutorService workers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128), Thread.ofVirtual().name("workspace-git-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    private final ExecutorService scans = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), Thread.ofVirtual().name("git-discovery-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    @org.springframework.beans.factory.annotation.Value("${workspace.git.mutation-workers:4}") private int workerCount = 4;
    @org.springframework.beans.factory.annotation.Value("${workspace.git.scan-workers:2}") private int scanCount = 2;
    @jakarta.annotation.PostConstruct void configureWorkers() {
        resize((ThreadPoolExecutor) workers, workerCount); resize((ThreadPoolExecutor) scans, scanCount);
    }
    private void resize(ThreadPoolExecutor executor, int size) {
        size = Math.max(1, size); executor.setCorePoolSize(Math.min(size, executor.getCorePoolSize()));
        executor.setMaximumPoolSize(size); executor.setCorePoolSize(size);
    }
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private static final Set<String> ACTIONS = Set.of("fetch", "pull", "pull-rebase", "push", "publish", "stash", "unstage-all", "discard", "delete-untracked", "stash-apply", "stash-pop", "stash-drop");
    private static final class Job {
        final String id = UUID.randomUUID().toString(); final Long workspace;
        final Instant created = Instant.now(); final Map<String, Outcome> outcomes = new LinkedHashMap<>();
        volatile Discovery result; final Consumer<Job> changed;
        Job(Long workspace, Consumer<Job> changed) { this.workspace = workspace; this.changed = changed; }
        void outcome(String id, String state, String message) {
            synchronized (this) { outcomes.put(id, new Outcome(id, state, message)); }
            changed.accept(this);
        }
        synchronized Operation view() {
            boolean running = outcomes.values().stream().anyMatch(o -> o.state().equals("queued") || o.state().equals("running"));
            return new Operation(id, running ? "running" : "done", List.copyOf(outcomes.values()), result);
        }
    }
    private volatile SimpMessagingTemplate messaging;
    /** Optional so the service also runs without a WebSocket broker; clients then fall back to polling. */
    @org.springframework.beans.factory.annotation.Autowired(required = false) public void setMessaging(SimpMessagingTemplate messaging) { this.messaging = messaging; }
    private void publish(Job job) {
        SimpMessagingTemplate template = messaging;
        if (template == null) return;
        try { template.convertAndSend("/topic/workspace-git/" + job.workspace, job.view()); }
        catch (RuntimeException ignored) { /* Polling remains authoritative when a broadcast cannot be delivered. */ }
    }
    public GitRepositoryActionService(WorkspaceGitDiscoveryService discovery, GitChangeService changes, GitCommandRunner git) {
        this.discovery = discovery; this.changes = changes; this.git = git;
    }
    public synchronized Operation discover(Workspace workspace) { return discover(workspace, true); }
    /** Without {@code refresh}, the last completed discovery is returned immediately while the workspace's projects are unchanged. */
    public synchronized Operation discover(Workspace workspace, boolean refresh) {
        Job job = create(workspace);
        var last = refresh ? Optional.<Discovery>empty() : discovery.last(workspace);
        if (last.isPresent()) { job.result = last.get(); job.outcome("discovery", "success", "Showing the last repository discovery. Refresh repositories to check the workspace projects again."); return job.view(); }
        job.outcome("discovery", "running", "Finding the repositories of the workspace projects…");
        submit(scans, job, "discovery", (java.util.function.Supplier<Done>) () -> {
            Discovery result = discovery.discover(workspace); job.result = result;
            if (result.visited() == 0) return new Done("success", "This workspace has no projects. The Git view lists the repositories of workspace projects; add projects to see them here.");
            return new Done("success", result.repositories().size() + (result.repositories().size() == 1 ? " repository" : " repositories") + " from " + result.visited() + (result.visited() == 1 ? " project." : " projects."));
        }); return job.view();
    }
    public Operation operation(Workspace workspace, String id) {
        Job job = jobs.get(id);
        if (job == null || !job.workspace.equals(workspace.getId())) throw new GitProblem(404, "Operation is unavailable or expired. Refresh repository state.");
        return job.view();
    }
    private synchronized Job create(Workspace workspace) {
        Instant expiry = Instant.now().minus(Duration.ofHours(1));
        jobs.entrySet().removeIf(entry -> entry.getValue().created.isBefore(expiry) && entry.getValue().view().state().equals("done"));
        if (jobs.size() >= 256) {
            jobs.values().stream().filter(job -> job.view().state().equals("done")).min(Comparator.comparing(job -> job.created)).ifPresent(job -> jobs.remove(job.id));
            if (jobs.size() >= 256) throw new GitProblem(429, "Too many Git operations are running. Wait for them to finish.");
        }
        Job job = new Job(workspace.getId(), this::publish); jobs.put(job.id, job); return job;
    }
    /** A finished target's state and message; "committed" means the commit exists but the following push did not succeed. */
    private record Done(String state, String message) { }
    private void submit(ExecutorService executor, Job job, String id, Runnable action) {
        submit(executor, job, id, (java.util.function.Supplier<Done>) () -> { action.run(); return new Done("success", "Completed"); });
    }
    private void submit(ExecutorService executor, Job job, String id, java.util.function.Supplier<Done> action) {
        try { executor.execute(() -> {
            job.outcome(id, "running", "Running…");
            try { Done done = action.get(); job.outcome(id, done.state(), done.message()); }
            catch (Exception error) { job.outcome(id, error instanceof GitProblem p && (p.status() == 504 || p.status() == 503) ? "uncertain" : "failed", error.getMessage() == null ? "Git operation failed. Refresh and check the repository." : error.getMessage()); }
        }); } catch (RejectedExecutionException e) { job.outcome(id, "failed", "Git queue is full. Wait for other operations to finish."); }
    }
    public Operation commits(Workspace workspace, SelectedCommit request) {
        validateMessage(request.message());
        if (request.targets() == null || request.targets().isEmpty() || request.targets().size() > 256)
            throw new GitProblem(400, "Select files in between 1 and 256 repositories.");
        GitSelectedCommitService selected = new GitSelectedCommitService(git, changes);
        Set<String> ids = new HashSet<>();
        for (CommitTarget target : request.targets()) {
            if (target == null || target.id() == null || !ids.add(target.id())) throw new GitProblem(400, "Select each repository once.");
            selected.validate(discovery.repository(workspace, target.id()), target);
        }
        Job job = create(workspace);
        request.targets().forEach(target -> job.outcome(target.id(), "queued", "Selected-file commit queued"));
        request.targets().forEach(target -> submit(workers, job, target.id(), () -> {
            Resolved repository = discovery.repository(workspace, target.id());
            // The coordinator is reentrant, so the commit and its push form one serialized unit for the repository.
            return GitMutationCoordinator.mutate(repository.commonDir(), () -> { selected.commit(repository, target, request.message()); return afterCommit(repository, request.push()); });
        }));
        return job.view();
    }
    /**
     * Names the created commit and, for Commit & Push, pushes it. A push problem never undoes or repeats the commit:
     * the target is reported as "committed" (or "uncertain" when the push may have reached the remote).
     */
    private Done afterCommit(Resolved repository, boolean push) {
        String head = changes.head(repository); String commit = head.substring(0, Math.min(10, head.length()));
        if (!push) return new Done("success", "Committed " + commit);
        if (changes.status(repository).upstream() == null)
            return new Done("committed", "Committed " + commit + " locally; not pushed because the branch has no upstream. Use Publish branch to push it.");
        try { git.run(repository.root(), null, Map.of(), true, "push").require(); }
        catch (GitProblem problem) {
            boolean uncertain = problem.status() == 504 || problem.status() == 503;
            return new Done(uncertain ? "uncertain" : "committed", "Committed " + commit + " locally; push " + (uncertain ? "outcome is uncertain" : "failed") + ": " + problem.getMessage());
        }
        return new Done("success", "Committed " + commit + " and pushed");
    }
    private void validateMessage(String message) {
        if (message == null || message.isBlank()) throw new GitProblem(400, "Enter a commit message.");
        if (message.length() > 65536) throw new GitProblem(400, "Commit message is too long.");
    }
    public Operation actions(Workspace workspace, Action request) {
        if (request.action() == null || !ACTIONS.contains(request.action()) || request.targets() == null || request.targets().isEmpty() || request.targets().size() > 256)
            throw new GitProblem(400, "Choose an available action and between 1 and 256 repositories.");
        boolean fileScoped = Set.of("discard", "delete-untracked").contains(request.action());
        if (request.path() != null && (!fileScoped || request.targets().size() != 1)) throw new GitProblem(400, "File scope is only available for a single-repository discard or deletion.");
        if (request.action().equals("delete-untracked") && (request.path() == null || !"confirmed".equals(request.confirmation()))) throw new GitProblem(400, "Choose one untracked file and confirm its deletion.");
        if (request.action().equals("stash") && !"confirmed".equals(request.confirmation())) throw new GitProblem(400, "Confirm the stash action first.");
        if (request.action().equals("pull-rebase") && !"confirmed".equals(request.confirmation())) throw new GitProblem(400, "Confirm the rebase before pulling.");
        if (request.action().startsWith("stash-")) {
            if (request.targets().size() != 1 || request.stash() == null || !STASH.matcher(request.stash()).matches()) throw new GitProblem(400, "Choose one stash of one repository.");
            GitHistoryService.commit(request.stashSha());
            if (request.action().equals("stash-drop") && !"confirmed".equals(request.confirmation())) throw new GitProblem(400, "Confirm dropping the stash first.");
        }
        if (request.action().equals("discard")) {
            String expected = request.path() == null ? workspace.getName() : "confirmed";
            if (!Objects.equals(expected, request.confirmation())) throw new GitProblem(400, "Confirm the discard action before continuing.");
        }
        Map<String, Target> targets = new LinkedHashMap<>();
        for (Target target : request.targets()) { if (target == null || target.id() == null) throw new GitProblem(400, "Select valid repositories."); discovery.repository(workspace, target.id()); targets.putIfAbsent(target.id(), target); }
        Job job = create(workspace);
        for (String id : targets.keySet()) job.outcome(id, "queued", request.action() + " queued");
        targets.forEach((id, target) -> submit(workers, job, id, () -> {
            Resolved repository = discovery.repository(workspace, id);
            GitMutationCoordinator.mutate(repository.commonDir(), () -> { execute(discovery.repository(workspace, id), target, request); return null; });
        }));
        return job.view();
    }
    private void execute(Resolved repository, Target target, Action action) {
        switch (action.action()) {
            case "fetch" -> git.run(repository.root(), null, Map.of(), true, "fetch").require();
            case "pull", "push" -> {
                if (changes.status(repository).upstream() == null) throw new GitProblem(422, "No upstream is configured. Configure the branch upstream with Git, then refresh.");
                if (action.action().equals("pull")) git.run(repository.root(), null, Map.of(), true, "pull", "--ff-only").require();
                else git.run(repository.root(), null, Map.of(), true, "push").require();
            }
            case "pull-rebase" -> {
                if (changes.status(repository).upstream() == null) throw new GitProblem(422, "No upstream is configured. Configure the branch upstream with Git, then refresh.");
                var result = git.run(repository.root(), null, Map.of(), true, "pull", "--rebase", "--no-autostash");
                if (result.code() != 0 && (Files.exists(repository.gitDir().resolve("rebase-merge")) || Files.exists(repository.gitDir().resolve("rebase-apply"))))
                    throw new GitProblem(422, "Rebase stopped on conflicts. Resolve them in your editor, stage the files, then run git rebase --continue (or git rebase --abort to undo the pull).");
                result.require();
            }
            case "delete-untracked" -> deleteUntracked(repository, action.path());
            case "stash-apply", "stash-pop", "stash-drop" -> stash(repository, action);
            case "publish" -> {
                List<String> remotes = remotes(repository);
                if (remotes.isEmpty()) throw new GitProblem(422, "No remote is configured. Add a remote with Git, then publish the branch.");
                String remote = action.remote() != null ? action.remote() : remotes.size() == 1 ? remotes.getFirst() : remotes.contains("origin") ? "origin" : null;
                if (remote == null || !remotes.contains(remote)) throw new GitProblem(400, "Choose one of the configured remotes: " + String.join(", ", remotes) + ".");
                Status status = changes.status(repository);
                if (status.unborn() || status.branch().equals("detached HEAD")) throw new GitProblem(422, "Check out a branch with at least one commit before publishing.");
                git.run(repository.root(), null, Map.of(), true, "push", "--set-upstream", remote, "HEAD:refs/heads/" + status.branch()).require();
            }
            case "stash" -> {
                changes.expect(repository, target.version());
                git.run(repository.root(), null, Map.of(), true, action.includeUntracked() ? new String[]{"stash", "push", "-u"} : new String[]{"stash", "push"}).require();
            }
            case "unstage-all" -> changes.select(repository, new Selection(null, "staged", target.version(), null, true), false);
            case "discard" -> {
                changes.expect(repository, target.version());
                List<String> paths = new ArrayList<>();
                for (FileChange file : changes.status(repository).files()) {
                    if (action.path() != null && !file.path().equals(action.path())) continue;
                    if (file.kind().equals("conflict")) throw new GitProblem(422, "Resolve conflicts before discarding repository changes.");
                    if (!file.index().equals("?") && !file.index().equals("D") && !file.working().equals(".") && !file.kind().equals("gitlink")) {
                        WorkspaceGitDiscoveryService.safePath(repository, file.path()); paths.add(file.path());
                    }
                }
                if (paths.isEmpty()) throw new GitProblem(422, "There are no tracked unstaged changes to discard.");
                List<String> command = new ArrayList<>(List.of("restore", "--worktree", "--")); command.addAll(paths);
                changes.expect(repository, target.version());
                git.run(repository.root(), command.toArray(String[]::new)).require();
            }
            default -> throw new GitProblem(400, "Unsupported Git action.");
        }
    }
    private static final java.util.regex.Pattern STASH = java.util.regex.Pattern.compile("stash@\\{\\d{1,6}}");
    public List<StashEntry> stashes(Resolved repository) {
        String output = git.run(repository.root(), "stash", "list", "-z", "--format=%gd%x1f%H%x1f%gs%x1f%ct").require().text();
        List<StashEntry> entries = new ArrayList<>();
        var subject = java.util.regex.Pattern.compile("(?:WIP on|On) ([^:]+): (.*)", java.util.regex.Pattern.DOTALL);
        for (String record : output.split("\0")) {
            String[] fields = record.split("\u001f", -1); if (fields.length < 4) continue;
            var matcher = subject.matcher(fields[2]);
            entries.add(new StashEntry(fields[0], fields[1], matcher.matches() ? matcher.group(2) : fields[2], matcher.matches() ? matcher.group(1) : null, Long.parseLong(fields[3].strip())));
        }
        return List.copyOf(entries);
    }
    /** Stash mutations act only if the reference still names the stash commit the user selected. */
    private void stash(Resolved repository, Action action) {
        var current = git.run(repository.root(), "rev-parse", "--verify", "--quiet", action.stash());
        if (current.code() != 0 || !current.text().strip().equals(action.stashSha())) throw new GitProblem(409, "The stash list changed. Refresh the stashes and choose again.");
        String command = action.action().substring("stash-".length());
        var result = git.run(repository.root(), null, Map.of(), false, "stash", command, action.stash());
        if (result.code() == 0) return;
        boolean conflicts = changes.status(repository).files().stream().anyMatch(file -> file.kind().equals("conflict"));
        if (conflicts) throw new GitProblem(422, "The stash was applied with conflicts and is kept in the list. Resolve the conflicts in your editor, then stage the files.");
        result.require();
    }
    /** Deletes one untracked, non-ignored file or folder after revalidating it against a fresh status; links are never followed. */
    private void deleteUntracked(Resolved repository, String path) {
        Path target = WorkspaceGitDiscoveryService.safePath(repository, path);
        FileChange entry = GitChangeService.find(changes.status(repository), path, "changes");
        if (!entry.index().equals("?") || !Set.of("untracked", "untracked-dir", "symlink").contains(entry.kind())) throw GitProblem.stale();
        boolean folder = entry.kind().equals("untracked-dir");
        // Files inside a collapsed folder are matched through the folder; confirm Git itself still lists them as untracked.
        String listed = git.run(repository.root(), "ls-files", "--others", "--exclude-standard", "-z", "--", path).require().text();
        if (listed.isEmpty() || !folder && !Arrays.asList(listed.split("\0")).contains(path)) throw GitProblem.stale();
        try {
            if (!folder) { Files.delete(target); return; }
            List<Path> contents;
            try (var walk = Files.walk(target)) { contents = walk.sorted(Comparator.reverseOrder()).toList(); }
            if (contents.stream().anyMatch(item -> item.getFileName().toString().equals(".git"))) throw new GitProblem(422, "This folder contains another Git repository and is not deleted. Remove it outside the application if intended.");
            for (Path item : contents) Files.delete(item);
        } catch (java.nio.file.NoSuchFileException e) { throw GitProblem.stale();
        } catch (java.io.IOException e) { throw new GitProblem(422, "Cannot delete " + path + ": " + e.getMessage()); }
    }
    /** Configured remote names of the repository, in Git's order. */
    public List<String> remotes(Resolved repository) {
        return Arrays.stream(git.text(repository.root(), "remote").split("\n")).filter(name -> !name.isBlank()).toList();
    }
    @PreDestroy public void close() { workers.shutdownNow(); scans.shutdownNow(); }
}
