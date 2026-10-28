package net.olaba.mvnbuilder.service.git;

import net.olaba.mvnbuilder.entities.Workspace;
import org.springframework.stereotype.Service;
import static net.olaba.mvnbuilder.service.git.GitModels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Workspace-wide push/pull overview built from local refs only; nothing here contacts a remote. */
@Service
public class GitSyncService {
    private final WorkspaceGitDiscoveryService discovery;
    private final GitChangeService changes;
    private final GitCommandRunner git;
    public GitSyncService(WorkspaceGitDiscoveryService discovery, GitChangeService changes, GitCommandRunner git) {
        this.discovery = discovery; this.changes = changes; this.git = git;
    }
    public SyncSummary summary(Workspace workspace) {
        Discovery known = discovery.last(workspace).orElseThrow(() -> new GitProblem(409, "Repositories have not been discovered yet. Refresh repositories first."));
        List<SyncRepository> repositories = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Concurrency is bounded by the command runner's local read capacity.
            List<Future<SyncRepository>> pending = known.repositories().stream().map(repository -> executor.submit(() -> repository(workspace, repository))).toList();
            for (Future<SyncRepository> future : pending) repositories.add(future.get());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new GitProblem(503, "Synchronization overview was interrupted.");
        } catch (ExecutionException e) { throw new GitProblem(422, "Cannot build the synchronization overview: " + e.getCause().getMessage()); }
        return new SyncSummary(List.copyOf(repositories));
    }
    private SyncRepository repository(Workspace workspace, Repository descriptor) {
        try {
            Resolved repository = discovery.repository(workspace, descriptor.id());
            Status status = changes.status(repository);
            String refs = git.run(repository.root(), "for-each-ref", "--format=%(refname:short)%00%(upstream:short)%00%(upstream:track,nobracket)%00%(HEAD)", "refs/heads").require().text();
            List<SyncBranch> branches = new ArrayList<>();
            for (String row : refs.split("\n")) {
                if (row.isEmpty()) continue;
                String[] fields = row.split("\0", -1);
                branches.add(branch(fields[0], fields[3].equals("*"), fields[1].isEmpty() ? null : fields[1], fields[2]));
            }
            branches.sort(Comparator.comparing((SyncBranch branch) -> !branch.current()));
            return new SyncRepository(descriptor.id(), descriptor.name(), descriptor.displayName(), descriptor.absolutePath(), status.branch(), status.files().size(), lastFetch(repository), List.copyOf(branches), null);
        } catch (GitProblem e) {
            return new SyncRepository(descriptor.id(), descriptor.name(), descriptor.displayName(), descriptor.absolutePath(), null, 0, null, List.of(), e.getMessage());
        }
    }
    /** Parses %(upstream:track,nobracket): "ahead 2, behind 1", "ahead 2", "behind 1", "gone" or empty when in sync. */
    static SyncBranch branch(String name, boolean current, String upstream, String track) {
        if (upstream == null) return new SyncBranch(name, current, null, null, null, false);
        if (track.equals("gone")) return new SyncBranch(name, current, upstream, null, null, true);
        int ahead = 0, behind = 0;
        for (String part : track.split(",")) {
            String[] words = part.strip().split(" ");
            if (words.length == 2 && words[0].equals("ahead")) ahead = Integer.parseInt(words[1]);
            if (words.length == 2 && words[0].equals("behind")) behind = Integer.parseInt(words[1]);
        }
        return new SyncBranch(name, current, upstream, ahead, behind, false);
    }
    private static Long lastFetch(Resolved repository) {
        for (Path directory : List.of(repository.gitDir(), repository.commonDir())) {
            try { return Files.getLastModifiedTime(directory.resolve("FETCH_HEAD")).toMillis(); }
            catch (java.io.IOException ignored) { /* Not fetched through this directory. */ }
        }
        return null;
    }
}
