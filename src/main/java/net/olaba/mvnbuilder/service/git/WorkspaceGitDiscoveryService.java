package net.olaba.mvnbuilder.service.git;

import org.springframework.stereotype.Service;
import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.service.git.WorkspaceGitProjects.ProjectFolder;
import static net.olaba.mvnbuilder.service.git.GitModels.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Repositories of the Git view are the working trees that contain the workspace's registered projects. */
@Service
public class WorkspaceGitDiscoveryService {
    /** Project membership is re-read at most this often on the per-request validation path. */
    private static final long MEMBERSHIP_TTL_NANOS = TimeUnit.SECONDS.toNanos(2);
    private final GitCommandRunner git;
    private final WorkspaceGitProjects projects;
    private final Map<Long, Map<String, Resolved>> repositories = new ConcurrentHashMap<>();
    private final Map<Long, AtomicLong> generations = new ConcurrentHashMap<>();
    private record Completed(String fingerprint, Discovery discovery) { }
    private final Map<Long, Completed> completed = new ConcurrentHashMap<>();
    private record Membership(String fingerprint, long read) { }
    private final Map<Long, Membership> memberships = new ConcurrentHashMap<>();
    public WorkspaceGitDiscoveryService(GitCommandRunner git, WorkspaceGitProjects projects) { this.git = git; this.projects = projects; }
    public Discovery discover(Workspace workspace) {
        long generation = generations.computeIfAbsent(workspace.getId(), ignored -> new AtomicLong()).incrementAndGet();
        List<ProjectFolder> folders = projects.folders(workspace);
        String fingerprint = fingerprint(folders);
        memberships.put(workspace.getId(), new Membership(fingerprint, System.nanoTime()));
        List<String> warnings = new ArrayList<>();
        Map<Path, Resolved> roots = new LinkedHashMap<>();
        boolean complete = true;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Concurrency is bounded by the command runner's local read capacity.
            List<Future<Resolved>> pending = folders.stream().map(folder -> executor.submit(() -> resolveProject(workspace, folder))).toList();
            for (int i = 0; i < pending.size(); i++) {
                try { Resolved resolved = pending.get(i).get(); roots.putIfAbsent(resolved.root(), resolved); }
                catch (ExecutionException e) { warnings.add(e.getCause() instanceof GitProblem problem ? problem.getMessage() : "Project " + folders.get(i).name() + ": " + e.getCause().getMessage()); }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); complete = false; warnings.add("Repository discovery was interrupted. Refresh repositories.");
        }
        Map<Path, String> names = displayNames(roots.keySet());
        Map<String, Resolved> found = new LinkedHashMap<>();
        for (Resolved resolved : roots.values()) {
            Repository d = resolved.descriptor();
            Resolved named = new Resolved(new Repository(d.id(), d.name(), names.get(resolved.root()), d.absolutePath()), resolved.root(), resolved.gitDir(), resolved.commonDir());
            found.put(d.id(), named);
        }
        var descriptors = found.values().stream().map(Resolved::descriptor).sorted(Comparator.comparing(Repository::displayName, String.CASE_INSENSITIVE_ORDER)).toList();
        Discovery result = new Discovery(descriptors, List.copyOf(warnings), complete, folders.size());
        if (generations.get(workspace.getId()).get() == generation) { repositories.put(workspace.getId(), Map.copyOf(found)); completed.put(workspace.getId(), new Completed(fingerprint, result)); }
        return result;
    }
    /** The last completed discovery, while the workspace's registered projects are unchanged. */
    public Optional<Discovery> last(Workspace workspace) {
        Completed last = completed.get(workspace.getId());
        if (last == null || !last.fingerprint().equals(membership(workspace, true))) return Optional.empty();
        return Optional.of(last.discovery());
    }
    public Resolved repository(Workspace workspace, String id) {
        Resolved known = repositories.getOrDefault(workspace.getId(), Map.of()).get(id);
        if (known == null) throw GitProblem.unknownRepository();
        // Adding or removing projects can change which repositories belong to the workspace; rediscovery decides.
        Completed last = completed.get(workspace.getId());
        if (last == null || !last.fingerprint().equals(membership(workspace, false))) throw GitProblem.unknownRepository();
        if (unchanged(workspace, known)) return known;
        Resolved current = resolve(workspace, known.root(), known.descriptor().displayName());
        if (!current.descriptor().id().equals(id)) throw GitProblem.stale();
        return current;
    }
    private String membership(Workspace workspace, boolean fresh) {
        Membership cached = memberships.get(workspace.getId());
        if (!fresh && cached != null && System.nanoTime() - cached.read() < MEMBERSHIP_TTL_NANOS) return cached.fingerprint();
        String fingerprint = fingerprint(projects.folders(workspace));
        memberships.put(workspace.getId(), new Membership(fingerprint, System.nanoTime()));
        return fingerprint;
    }
    static String fingerprint(List<ProjectFolder> folders) {
        List<String> rows = folders.stream().map(folder -> folder.id() + "\0" + folder.folder()).sorted().toList();
        return hash(String.join("\n", rows).getBytes(StandardCharsets.UTF_8));
    }
    /** Folder names, extended with parent folders only as far as needed to tell equal names apart. */
    public static Map<Path, String> displayNames(Collection<Path> roots) {
        Map<Path, String> names = new HashMap<>();
        for (Path root : roots) {
            int depth = 1;
            while (depth < root.getNameCount()) {
                int current = depth;
                if (roots.stream().noneMatch(other -> !other.equals(root) && suffix(other, current).equals(suffix(root, current)))) break;
                depth++;
            }
            names.put(root, suffix(root, depth));
        }
        return names;
    }
    private static String suffix(Path path, int depth) {
        int count = path.getNameCount();
        if (count == 0) return path.toString();
        return path.subpath(Math.max(0, count - depth), count).toString().replace('\\', '/');
    }
    /** Revalidates a cached identity from filesystem metadata only, without starting Git processes. */
    private boolean unchanged(Workspace workspace, Resolved known) {
        try {
            Path root = known.root();
            if (Files.isSymbolicLink(root) || !root.toRealPath().equals(root)) return false;
            if (!Files.exists(root.resolve(".git"), LinkOption.NOFOLLOW_LINKS) || !Files.isDirectory(known.gitDir())) return false;
            return identity(workspace, root, known.gitDir()).equals(known.descriptor().id());
        } catch (IOException e) { return false; }
    }
    private static String identity(Workspace workspace, Path root, Path gitDir) throws IOException {
        String key = workspace.getId() + "\0" + root + "\0" + gitDir + "\0" + Files.readAttributes(gitDir, BasicFileAttributes.class).fileKey();
        return hash(key.getBytes(StandardCharsets.UTF_8));
    }
    /** Finds the working tree that contains a registered project folder; the folder may be any subdirectory of it. */
    private Resolved resolveProject(Workspace workspace, ProjectFolder project) {
        String label = "Project " + project.name();
        if (project.folder() == null) throw new GitProblem(422, label + " has no folder. Check the workspace base folder or the project path.");
        Path folder;
        try { folder = project.folder().toRealPath(); }
        catch (IOException e) { throw new GitProblem(404, label + ": folder " + project.folder() + " is missing or unreadable."); }
        if (!Files.isDirectory(folder)) throw new GitProblem(422, label + ": " + folder + " is not a folder.");
        var top = git.run(folder, "rev-parse", "--show-toplevel");
        if (top.code() != 0) {
            var bare = git.run(folder, "rev-parse", "--is-bare-repository");
            if (bare.code() == 0 && bare.text().strip().equals("true")) throw new GitProblem(422, label + ": " + folder + " is a bare repository and has no editable working tree.");
            throw new GitProblem(422, label + ": " + folder + " is not inside a Git repository.");
        }
        try { return resolve(workspace, Path.of(top.text().strip()).toRealPath(), null); }
        catch (IOException e) { throw new GitProblem(404, label + ": its repository is no longer accessible."); }
    }
    private Resolved resolve(Workspace workspace, Path candidate, String displayName) {
        try {
            Path root = Path.of(git.text(candidate, "rev-parse", "--show-toplevel")).toRealPath();
            if (!root.equals(candidate.toRealPath())) throw new GitProblem(403, "Candidate is not a repository root.");
            Path gitDir = Path.of(git.text(root, "rev-parse", "--absolute-git-dir")).toRealPath();
            Path common = Path.of(git.text(root, "rev-parse", "--path-format=absolute", "--git-common-dir")).toRealPath();
            String name = root.getFileName() == null ? root.toString() : root.getFileName().toString();
            return new Resolved(new Repository(identity(workspace, root, gitDir), name, displayName == null ? name : displayName, root.toString()), root, gitDir, common);
        } catch (IOException e) { throw new GitProblem(404, "Repository is no longer accessible. Refresh repositories."); }
    }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static Path safePath(Resolved repository, String name) {
        if (name == null || name.isEmpty() || name.indexOf('\0') >= 0) throw new GitProblem(400, "Select a repository-relative file.");
        Path relative;
        try { relative = Path.of(name); } catch (InvalidPathException e) { throw new GitProblem(400, "Invalid file path."); }
        if (relative.isAbsolute()) throw new GitProblem(403, "Absolute file paths are not accepted.");
        for (Path part : relative) if (part.toString().equals("..") || part.toString().equalsIgnoreCase(".git")) throw new GitProblem(403, "Git administrative and traversal paths are not accessible.");
        Path target = repository.root().resolve(relative).normalize();
        if (!target.startsWith(repository.root()) || target.equals(repository.root())) throw new GitProblem(403, "File is outside this repository.");
        Path parent = target.getParent();
        try {
            while (!Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) parent = parent.getParent();
            if (!parent.toRealPath().startsWith(repository.root())) throw new GitProblem(403, "File path crosses a symlink outside this repository.");
            // A nested working tree belongs to its own discovered repository.
            for (Path walk = target.getParent(); walk != null && !walk.equals(repository.root()); walk = walk.getParent())
                if (Files.exists(walk.resolve(".git"), LinkOption.NOFOLLOW_LINKS)) throw new GitProblem(403, "Select this file from its own repository.");
        } catch (IOException e) { throw new GitProblem(422, "Cannot validate file path. Refresh repositories."); }
        return target;
    }
}
