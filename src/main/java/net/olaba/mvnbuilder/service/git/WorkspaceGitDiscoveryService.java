package net.olaba.mvnbuilder.service.git;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import net.olaba.mvnbuilder.entities.Workspace;
import static net.olaba.mvnbuilder.service.git.GitModels.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class WorkspaceGitDiscoveryService {
    private final GitCommandRunner git;
    private final Map<Long, Map<String, Resolved>> repositories = new ConcurrentHashMap<>();
    private final Map<Long, AtomicLong> generations = new ConcurrentHashMap<>();
    private record Completed(Path base, Discovery discovery) { }
    private final Map<Long, Completed> completed = new ConcurrentHashMap<>();
    @Value("${workspace.git.scan-directory-limit:100000}") private long limit = 100000;
    /** Build output and dependency folders are skipped by name; they rarely hold repositories and can be huge. */
    private volatile Set<String> excluded = Set.of("target", "node_modules", "build", "dist", "out", ".idea", ".gradle", ".mvn");
    @Value("${workspace.git.scan-exclude:target,node_modules,build,dist,out,.idea,.gradle,.mvn}") public void configureExclusions(String names) {
        excluded = Arrays.stream(names.split(",")).map(String::strip).filter(name -> !name.isEmpty()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    public WorkspaceGitDiscoveryService(GitCommandRunner git) { this.git = git; }
    public Discovery discover(Workspace workspace) {
        long generation = generations.computeIfAbsent(workspace.getId(), ignored -> new AtomicLong()).incrementAndGet();
        Path base = base(workspace);
        Map<String, Resolved> found = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        long[] visited = {0}; boolean[] complete = {true};
        try {
            Files.walkFileTree(base, new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(base) && dir.getFileName().toString().equalsIgnoreCase(".git")) return FileVisitResult.SKIP_SUBTREE;
                    if (!dir.equals(base) && excluded.contains(dir.getFileName().toString())) return FileVisitResult.SKIP_SUBTREE;
                    if (Thread.currentThread().isInterrupted()) { complete[0] = false; warnings.add("Repository discovery was interrupted. Refresh repositories."); return FileVisitResult.TERMINATE; }
                    if (++visited[0] > limit) { complete[0] = false; warnings.add("Scan limit reached at " + dir + ". Increase workspace.git.scan-directory-limit and refresh."); return FileVisitResult.TERMINATE; }
                    if (Files.exists(dir.resolve(".git"), LinkOption.NOFOLLOW_LINKS)) {
                        try {
                            Resolved resolved = resolve(workspace, base, dir);
                            found.put(resolved.descriptor().id(), resolved);
                        } catch (Exception e) { warnings.add(dir + ": " + e.getMessage()); }
                    } else if (Files.isRegularFile(dir.resolve("HEAD")) && Files.isDirectory(dir.resolve("objects")) && Files.isDirectory(dir.resolve("refs"))) {
                        warnings.add(dir + ": bare repositories have no editable working tree.");
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path file, IOException error) {
                    complete[0] = false; warnings.add("Cannot inspect " + file + ": " + error.getMessage()); return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) { throw new GitProblem(422, "Cannot scan base folder: " + e.getMessage()); }
        var descriptors = found.values().stream().map(Resolved::descriptor).sorted(Comparator.comparing(Repository::path)).toList();
        Discovery result = new Discovery(descriptors, List.copyOf(warnings), complete[0], visited[0]);
        if (generations.get(workspace.getId()).get() == generation) { repositories.put(workspace.getId(), Map.copyOf(found)); completed.put(workspace.getId(), new Completed(base, result)); }
        return result;
    }
    /** The last completed scan for the workspace's current base folder, if any. */
    public Optional<Discovery> last(Workspace workspace) {
        Completed last = completed.get(workspace.getId());
        if (last == null) return Optional.empty();
        // An unavailable base folder is reported by a fresh scan, not hidden behind an old result.
        try { return last.base().equals(base(workspace)) ? Optional.of(last.discovery()) : Optional.empty(); }
        catch (GitProblem e) { return Optional.empty(); }
    }
    public Resolved repository(Workspace workspace, String id) {
        Resolved known = repositories.getOrDefault(workspace.getId(), Map.of()).get(id);
        if (known == null) throw GitProblem.unknownRepository();
        Path base = base(workspace);
        if (unchanged(workspace, base, known)) return known;
        Resolved current = resolve(workspace, base, known.root());
        if (!current.descriptor().id().equals(id)) throw GitProblem.stale();
        return current;
    }
    /** Revalidates a cached identity from filesystem metadata only, without starting Git processes. */
    private boolean unchanged(Workspace workspace, Path base, Resolved known) {
        try {
            Path root = known.root();
            if (Files.isSymbolicLink(root) || !root.startsWith(base) || !root.toRealPath().equals(root)) return false;
            if (!Files.exists(root.resolve(".git"), LinkOption.NOFOLLOW_LINKS) || !Files.isDirectory(known.gitDir())) return false;
            return identity(workspace, base, root, known.gitDir()).equals(known.descriptor().id());
        } catch (IOException e) { return false; }
    }
    private static String identity(Workspace workspace, Path base, Path root, Path gitDir) throws IOException {
        String key = workspace.getId() + "\0" + base + "\0" + root + "\0" + gitDir + "\0" + Files.readAttributes(gitDir, BasicFileAttributes.class).fileKey();
        return hash(key.getBytes(StandardCharsets.UTF_8));
    }
    private Resolved resolve(Workspace workspace, Path base, Path candidate) {
        try {
            if (Files.isSymbolicLink(candidate) || !candidate.toRealPath().startsWith(base)) throw new GitProblem(403, "Repository is outside the workspace base folder.");
            Path root = Path.of(git.text(candidate, "rev-parse", "--show-toplevel")).toRealPath();
            if (!root.equals(candidate.toRealPath()) || !root.startsWith(base)) throw new GitProblem(403, "Candidate is not a workspace repository root.");
            Path gitDir = Path.of(git.text(root, "rev-parse", "--absolute-git-dir")).toRealPath();
            Path common = Path.of(git.text(root, "rev-parse", "--path-format=absolute", "--git-common-dir")).toRealPath();
            String relative = base.equals(root) ? "." : base.relativize(root).toString();
            return new Resolved(new Repository(identity(workspace, base, root, gitDir), root.getFileName().toString(), relative), root, gitDir, common);
        } catch (IOException e) { throw new GitProblem(404, "Repository is no longer accessible. Refresh repositories."); }
    }
    public static Path base(Workspace workspace) {
        if (workspace.getBasePath() == null || workspace.getBasePath().isBlank()) throw new GitProblem(422, "Set a base folder in Workspace settings to discover Git repositories.");
        try {
            Path base = Path.of(workspace.getBasePath()).toRealPath();
            if (!Files.isDirectory(base)) throw new IOException("not a directory");
            return base;
        } catch (Exception e) { throw new GitProblem(422, "Workspace base folder is unavailable. Check Workspace settings and folder access."); }
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
