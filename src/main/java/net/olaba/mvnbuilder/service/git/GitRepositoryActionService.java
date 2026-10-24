package net.olaba.mvnbuilder.service.git;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import net.olaba.mvnbuilder.entities.Workspace;
import static net.olaba.mvnbuilder.service.git.GitModels.*;
import java.nio.charset.StandardCharsets;
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
    private static final Set<String> ACTIONS = Set.of("fetch", "pull", "push", "stash", "unstage-all", "discard");
    private static final class Job {
        final String id = UUID.randomUUID().toString(); final Long workspace;
        final Instant created = Instant.now(); final Map<String, Outcome> outcomes = new LinkedHashMap<>();
        volatile Discovery result;
        Job(Long workspace) { this.workspace = workspace; }
        synchronized void outcome(String id, String state, String message) { outcomes.put(id, new Outcome(id, state, message)); }
        synchronized Operation view() {
            boolean running = outcomes.values().stream().anyMatch(o -> o.state().equals("queued") || o.state().equals("running"));
            return new Operation(id, running ? "running" : "done", List.copyOf(outcomes.values()), result);
        }
    }
    public GitRepositoryActionService(WorkspaceGitDiscoveryService discovery, GitChangeService changes, GitCommandRunner git) {
        this.discovery = discovery; this.changes = changes; this.git = git;
    }
    public synchronized Operation discover(Workspace workspace) {
        Job job = create(workspace); job.outcome("discovery", "running", "Scanning workspace folders…");
        submit(scans, job, "discovery", () -> job.result = discovery.discover(workspace)); return job.view();
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
        Job job = new Job(workspace.getId()); jobs.put(job.id, job); return job;
    }
    private void submit(ExecutorService executor, Job job, String id, Runnable action) {
        try { executor.execute(() -> {
            job.outcome(id, "running", "Running…");
            try { action.run(); job.outcome(id, "success", "Completed"); }
            catch (Exception error) { job.outcome(id, error instanceof GitProblem p && (p.status() == 504 || p.status() == 503) ? "uncertain" : "failed", error.getMessage() == null ? "Git operation failed. Refresh and check the repository." : error.getMessage()); }
        }); } catch (RejectedExecutionException e) { job.outcome(id, "failed", "Git queue is full. Wait for other operations to finish."); }
    }
    public Operation commit(Workspace workspace, String id, Commit request) {
        Resolved repository = discovery.repository(workspace, id);
        validateCommit(repository, request);
        Job job = create(workspace); job.outcome(id, "queued", "Commit queued");
        submit(workers, job, id, () -> GitMutationCoordinator.mutate(repository.root().toFile(), () -> {
            Resolved current = discovery.repository(workspace, id); validateCommit(current, request);
            git.run(current.root(), request.message().getBytes(StandardCharsets.UTF_8), Map.of(), true, "commit", "--file=-").require(); return null;
        })); return job.view();
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
        request.targets().forEach(target -> submit(workers, job, target.id(), () ->
                selected.commit(discovery.repository(workspace, target.id()), target, request.message())));
        return job.view();
    }
    private void validateMessage(String message) {
        if (message == null || message.isBlank()) throw new GitProblem(400, "Enter a commit message.");
        if (message.length() > 65536) throw new GitProblem(400, "Commit message is too long.");
    }
    private void validateCommit(Resolved repository, Commit request) {
        validateMessage(request.message());
        Status status = changes.status(repository);
        if (request.version() == null || !request.version().equals(status.stagedVersion())) throw GitProblem.stale();
        if (status.files().stream().anyMatch(file -> file.kind().equals("conflict"))) throw new GitProblem(422, "Resolve and stage all conflicts before committing.");
        if (status.files().stream().noneMatch(file -> !Set.of(".", "?").contains(file.index()))) throw new GitProblem(422, "Stage changes before committing.");
    }
    public Operation actions(Workspace workspace, Action request) {
        if (request.action() == null || !ACTIONS.contains(request.action()) || request.targets() == null || request.targets().isEmpty() || request.targets().size() > 256)
            throw new GitProblem(400, "Choose an available action and between 1 and 256 repositories.");
        if (request.path() != null && (!request.action().equals("discard") || request.targets().size() != 1)) throw new GitProblem(400, "File scope is only available for a single-repository discard.");
        if (request.action().equals("stash") && !"confirmed".equals(request.confirmation())) throw new GitProblem(400, "Confirm the stash action first.");
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
            GitMutationCoordinator.mutate(repository.root().toFile(), () -> { execute(discovery.repository(workspace, id), target, request); return null; });
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
    @PreDestroy public void close() { workers.shutdownNow(); scans.shutdownNow(); }
}
