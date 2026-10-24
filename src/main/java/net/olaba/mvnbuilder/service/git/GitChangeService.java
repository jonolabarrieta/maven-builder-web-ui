package net.olaba.mvnbuilder.service.git;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import static net.olaba.mvnbuilder.service.git.GitModels.*;
import static net.olaba.mvnbuilder.service.git.WorkspaceGitDiscoveryService.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;
import java.util.function.Consumer;

@Service
public class GitChangeService {
    private final GitCommandRunner git;
    @Value("${workspace.git.file-limit-bytes:1048576}") private int fileLimit = 1048576;
    public GitChangeService(GitCommandRunner git) { this.git = git; }
    public Status status(Resolved repository) {
        var root = repository.root();
        byte[] raw = git.run(root, "status", "--porcelain=v2", "-z", "--branch", "--untracked-files=all").require().output();
        String branch = "detached HEAD", upstream = null; Integer ahead = null, behind = null; boolean unborn = false;
        List<FileChange> files = new ArrayList<>();
        String[] records = new String(raw, StandardCharsets.UTF_8).split("\0", -1);
        for (int i = 0; i < records.length; i++) {
            String record = records[i];
            if (record.startsWith("# branch.head ")) branch = record.substring(14).equals("(detached)") ? "detached HEAD" : record.substring(14);
            else if (record.startsWith("# branch.oid ")) unborn = record.endsWith("(initial)");
            else if (record.startsWith("# branch.upstream ")) upstream = record.substring(18);
            else if (record.startsWith("# branch.ab ")) { var fields = record.substring(12).split(" "); ahead = Integer.valueOf(fields[0].substring(1)); behind = Integer.valueOf(fields[1].substring(1)); }
            else if (record.startsWith("? ")) {
                String name = record.substring(2); Path path = safePath(repository, name);
                String kind = Files.isSymbolicLink(path) ? "symlink" : Files.isDirectory(path) && Files.exists(path.resolve(".git")) ? "gitlink" : "untracked";
                files.add(new FileChange(name, null, "?", "?", kind));
            }
            else if (record.startsWith("1 ") || record.startsWith("2 ")) {
                boolean rename = record.charAt(0) == '2'; String[] f = record.split(" ", rename ? 10 : 9);
                String old = rename && i + 1 < records.length ? records[++i] : null;
                String kind = rename ? "rename" : "text";
                if (f[2].charAt(0) == 'S' || Arrays.asList(f[3], f[4], f[5]).contains("160000")) kind = "gitlink";
                else if (Arrays.asList(f[3], f[4], f[5]).contains("120000")) kind = "symlink";
                files.add(new FileChange(f[f.length - 1], old, f[1].substring(0, 1), f[1].substring(1, 2), kind));
            } else if (record.startsWith("u ")) { var f = record.split(" ", 11); files.add(new FileChange(f[10], null, f[1].substring(0, 1), f[1].substring(1, 2), "conflict")); }
        }
        String staged = stagedVersion(repository);
        StringBuilder version = new StringBuilder(staged).append(hash(raw));
        for (FileChange change : files) {
            try { Path path = safePath(repository, change.path()); version.append(change.path()).append(fileHash(path)); }
            catch (GitProblem e) { version.append("inaccessible:").append(change.path()); }
        }
        return new Status(branch, upstream, ahead, behind, unborn, hash(version.toString().getBytes(StandardCharsets.UTF_8)), staged, List.copyOf(files));
    }
    public String stagedVersion(Resolved repository) {
        String head = head(repository);
        return hash((head + "\0" + fileHash(repository.gitDir().resolve("index"))).getBytes(StandardCharsets.UTF_8));
    }
    public String head(Resolved repository) {
        var result = git.run(repository.root(), "rev-parse", "--verify", "HEAD");
        return result.code() == 0 ? result.text().stripTrailing() : "unborn";
    }
    public void expect(Resolved repository, String version) {
        if (version == null || !status(repository).version().equals(version)) throw GitProblem.stale();
    }
    private record Content(byte[] oldBytes, byte[] newBytes, boolean oldExists, boolean newExists, String partialReason) { }
    public Diff diff(Resolved repository, String path, String area) {
        if (!Set.of("changes", "staged").contains(area)) throw new GitProblem(400, "Select Changes or Staged Changes.");
        safePath(repository, path);
        for (int attempt = 0; attempt < 2; attempt++) {
            Status before = status(repository);
            FileChange change = find(before, path, area);
            String reason = switch (change.kind()) {
                case "conflict" -> "Resolve this conflict in your editor, then stage the whole file to mark it resolved.";
                case "symlink" -> "Symbolic link metadata; use whole-file actions. Link targets are not read.";
                case "gitlink" -> "Submodule pointer or nested working-tree changes; open the submodule repository to review its files.";
                default -> null;
            };
            if (reason != null) return new Diff(path, change.oldPath(), area, before.version(), change.kind(), reason, false, List.of());
            Content content;
            try { content = content(repository, change, area); }
            catch (GitProblem e) { if (e.status() != 413) throw e; return new Diff(path, change.oldPath(), area, before.version(), "oversized", e.getMessage(), false, List.of()); }
            if (!before.version().equals(status(repository).version())) continue;
            if (!text(content.oldBytes()) || !text(content.newBytes()))
                return new Diff(path, change.oldPath(), area, before.version(), "binary", "Binary or unsupported text encoding. Use whole-file actions.", false, List.of());
            List<Line> lines = lines(repository, content);
            boolean partial = !change.kind().equals("rename") && !lines.isEmpty() && content.partialReason() == null;
            String info = content.partialReason() != null ? content.partialReason() : change.kind().equals("rename") ? "Renamed file; stage or unstage the whole rename pair." : lines.isEmpty() ? "File metadata or empty-file change; use whole-file actions." : null;
            return new Diff(path, change.oldPath(), area, before.version(), change.kind(), info, partial, lines);
        }
        throw GitProblem.stale();
    }
    public static FileChange find(Status status, String path, String area) {
        return status.files().stream().filter(file -> file.path().equals(path))
                .filter(file -> area.equals("staged") ? !Set.of(".", "?").contains(file.index()) : !file.working().equals(".") || file.kind().equals("conflict"))
                .findFirst().orElseThrow(GitProblem::stale);
    }
    private Content content(Resolved repository, FileChange change, String area) {
        String path = change.path();
        byte[] oldBytes = new byte[0], newBytes = new byte[0]; boolean oldExists, newExists;
        if (area.equals("staged")) {
            String oldPath = change.oldPath() == null ? path : change.oldPath();
            oldExists = !change.index().equals("A") && !head(repository).equals("unborn");
            newExists = !change.index().equals("D");
            if (oldExists) oldBytes = blob(repository, "HEAD:" + oldPath);
            if (newExists) newBytes = blob(repository, ":" + path);
        } else {
            oldExists = !change.index().equals("?") && !change.index().equals("D");
            Path disk = safePath(repository, path); newExists = Files.exists(disk, LinkOption.NOFOLLOW_LINKS);
            if (oldExists) oldBytes = blob(repository, ":" + path);
            if (newExists) {
                if (Files.isSymbolicLink(disk) || !Files.isRegularFile(disk, LinkOption.NOFOLLOW_LINKS)) throw new GitProblem(413, "Special file content is unavailable. Use whole-file actions.");
                try (var stream = Files.newInputStream(disk)) { newBytes = stream.readNBytes(fileLimit + 1); }
                catch (IOException e) { throw new GitProblem(422, "Cannot read file: " + e.getMessage()); }
            }
        }
        if (oldBytes.length > fileLimit || newBytes.length > fileLimit) throw new GitProblem(413, "File exceeds workspace.git.file-limit-bytes. Use whole-file actions or increase the configured limit.");
        String partialReason = null;
        if (area.equals("changes") && newExists && text(newBytes)) {
            String[] attributes = git.run(repository.root(), "check-attr", "-z", "text", "eol", "filter", "working-tree-encoding", "--", path).require().text().split("\0", -1);
            Map<String, String> values = new HashMap<>();
            for (int i = 0; i + 2 < attributes.length; i += 3) values.put(attributes[i + 1], attributes[i + 2]);
            if (attribute(values.get("filter")) || attribute(values.get("working-tree-encoding"))) {
                partialReason = "This file uses Git content filters or working-tree encoding conversion. Use whole-file staging to preserve the configured conversion.";
            } else {
                String textAttribute = values.getOrDefault("text", "unspecified");
                String autocrlf = git.run(repository.root(), "config", "--get", "core.autocrlf").text().strip();
                boolean normalize = !textAttribute.equals("unset") && (attribute(textAttribute) || attribute(values.get("eol")) || autocrlf.equals("true") || autocrlf.equals("input"));
                if (normalize) {
                    boolean existingCrlf = new String(oldBytes, StandardCharsets.UTF_8).contains("\r\n");
                    if (existingCrlf && !textAttribute.equals("set") && !attribute(values.get("eol")))
                        partialReason = "This existing index uses CRLF with automatic EOL conversion. Use whole-file staging to preserve Git's conversion rules.";
                    else newBytes = new String(newBytes, StandardCharsets.UTF_8).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
                }
            }
        }
        return new Content(oldBytes, newBytes, oldExists, newExists, partialReason);
    }
    private boolean attribute(String value) { return value != null && !value.equals("unspecified") && !value.equals("unset"); }
    private byte[] blob(Resolved repository, String ref) {
        long size;
        try { size = Long.parseLong(git.text(repository.root(), "cat-file", "-s", ref)); }
        catch (NumberFormatException e) { throw new GitProblem(422, "Cannot read Git object size."); }
        if (size > fileLimit) throw new GitProblem(413, "File exceeds workspace.git.file-limit-bytes. Use whole-file actions.");
        return git.run(repository.root(), "cat-file", "blob", ref).require().output();
    }
    private boolean text(byte[] bytes) {
        for (byte b : bytes) if (b == 0) return false;
        try { StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)); return true; }
        catch (CharacterCodingException e) { return false; }
    }
    private List<Line> lines(Resolved repository, Content content) {
        Path temp = null;
        try {
            temp = Files.createTempDirectory("mvnbuilder-diff-");
            Path old = temp.resolve("old"), current = temp.resolve("new");
            Files.write(old, content.oldBytes()); Files.write(current, content.newBytes());
            var result = git.run(repository.root(), "diff", "--no-index", "--no-ext-diff", "--no-textconv", "--no-color", "--unified=3", "--", old.toString(), current.toString());
            if (result.code() > 1) result.require();
            List<Line> output = new ArrayList<>(); int oldNumber = 0, newNumber = 0, block = -1;
            Pattern hunk = Pattern.compile("@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@.*");
            for (String row : result.text().split("\n", -1)) {
                var matcher = hunk.matcher(row);
                if (matcher.matches()) { oldNumber = Math.max(1, Integer.parseInt(matcher.group(1))); newNumber = Math.max(1, Integer.parseInt(matcher.group(2))); block++; continue; }
                if (block < 0 || row.isEmpty()) continue;
                if (row.startsWith("\\ No newline") && !output.isEmpty()) {
                    Line previous = output.removeLast(); output.add(new Line(previous.id(), previous.type(), previous.oldNumber(), previous.newNumber(), previous.text(), false, previous.block())); continue;
                }
                char type = row.charAt(0);
                if (type != '+' && type != '-' && type != ' ') continue;
                output.add(new Line("l" + output.size(), Character.toString(type), type == '+' ? null : oldNumber, type == '-' ? null : newNumber, row.substring(1), true, block));
                if (type != '+') oldNumber++; if (type != '-') newNumber++;
            }
            return List.copyOf(output);
        } catch (IOException e) { throw new GitProblem(422, "Cannot prepare diff: " + e.getMessage());
        } finally { if (temp != null) try { Files.deleteIfExists(temp.resolve("old")); Files.deleteIfExists(temp.resolve("new")); Files.deleteIfExists(temp); } catch (IOException ignored) { } }
    }
    public void select(Resolved repository, Selection selection, boolean stage) {
        GitMutationCoordinator.mutate(repository.root().toFile(), () -> {
            expect(repository, selection.version());
            if (selection.all()) {
                if (stage) throw new GitProblem(400, "Select a file to stage.");
                updateIndex(repository, selection.version(), environment -> {
                    if (head(repository).equals("unborn")) git.run(repository.root(), null, environment, false, "read-tree", "--empty").require();
                    else git.run(repository.root(), null, environment, false, "read-tree", "HEAD").require();
                }); return null;
            }
            safePath(repository, selection.path());
            String area = stage ? "changes" : "staged";
            if (!area.equals(selection.area())) throw new GitProblem(400, "Selection does not match the staging action.");
            Status status = status(repository); FileChange change = find(status, selection.path(), area);
            if (selection.lines() != null && selection.lines().isEmpty()) throw new GitProblem(400, "Select at least one changed line, or use the whole-file action.");
            boolean partial = selection.lines() != null;
            if (!partial) {
                updateIndex(repository, selection.version(), environment -> {
                    List<String> paths = new ArrayList<>(); paths.add(change.path());
                    if (change.oldPath() != null) { safePath(repository, change.oldPath()); paths.add(change.oldPath()); }
                    if (stage) { stageWholeFiles(repository, environment, paths); return; }
                    List<String> args = new ArrayList<>();
                    if (head(repository).equals("unborn")) args.addAll(List.of("rm", "--cached", "-f", "--ignore-unmatch", "--"));
                    else args.addAll(List.of("restore", "--staged", "--source=HEAD", "--"));
                    args.addAll(paths); git.run(repository.root(), null, environment, false, args.toArray(String[]::new)).require();
                }); return null;
            }
            Diff diff = diff(repository, selection.path(), area);
            if (!diff.partial() || !diff.version().equals(selection.version())) throw GitProblem.stale();
            Set<String> chosen = new HashSet<>(selection.lines());
            Set<String> valid = new HashSet<>(); diff.lines().stream().filter(line -> !line.type().equals(" ")).forEach(line -> valid.add(line.id()));
            if (!valid.containsAll(chosen)) throw new GitProblem(400, "Selection contains unknown or unchanged lines.");
            Content content = content(repository, change, area);
            byte[] desired = selectedContent(content.oldBytes(), diff.lines(), chosen, stage);
            boolean remove = desired.length == 0 && ((!stage && !content.oldExists()) || (stage && !content.newExists()));
            updateIndex(repository, selection.version(), environment -> {
                if (remove) git.run(repository.root(), null, environment, false, "update-index", "--force-remove", "--", selection.path()).require();
                else {
                    String mode = git.run(repository.root(), "ls-files", "--stage", "-z", "--", selection.path()).require().text();
                    mode = mode.isEmpty() ? Files.isExecutable(safePath(repository, selection.path())) ? "100755" : "100644" : mode.substring(0, 6);
                    String object = git.run(repository.root(), desired, Map.of(), false, "hash-object", "-w", "--stdin").require().text().stripTrailing();
                    git.run(repository.root(), null, environment, false, "update-index", "--add", "--cacheinfo", mode, object, selection.path()).require();
                }
            }); return null;
        });
    }
    /** Update tracked files even below ignored directories; never force-add ignored new files. */
    void stageWholeFiles(Resolved repository, Map<String, String> environment, List<String> paths) {
        for (String path : paths) {
            boolean tracked = exactIndexEntry(repository, environment, path).length != 0;
            if (!tracked) {
                // A selected Changes file may be a new addition in the real index but absent from HEAD.
                byte[] original = exactIndexEntry(repository, Map.of(), path);
                if (original.length != 0) {
                    git.run(repository.root(), original, environment, false, "update-index", "-z", "--index-info").require();
                    tracked = true;
                }
            }
            git.run(repository.root(), null, environment, false, "add", tracked ? "-u" : "-A", "--", path).require();
        }
    }
    private byte[] exactIndexEntry(Resolved repository, Map<String, String> environment, String path) {
        byte[] entries = git.run(repository.root(), null, environment, false, "ls-files", "--stage", "-z", "--", path).require().output();
        for (String entry : new String(entries, StandardCharsets.UTF_8).split("\0")) {
            int tab = entry.indexOf('\t');
            if (tab >= 0 && entry.substring(tab + 1).equals(path)) return entries;
        }
        return new byte[0];
    }
    private byte[] selectedContent(byte[] original, List<Line> lines, Set<String> chosen, boolean stage) {
        List<String> base = splitLines(new String(original, StandardCharsets.UTF_8));
        Map<Integer, List<String>> additions = new LinkedHashMap<>(); Set<Integer> deletions = new HashSet<>();
        int cursor = 1;
        for (int i = 0; i < lines.size();) {
            Line first = lines.get(i);
            if (first.type().equals(" ")) { cursor = first.oldNumber() + 1; i++; continue; }
            int end = i;
            while (end < lines.size() && !lines.get(end).type().equals(" ") && lines.get(end).block() == first.block()) end++;
            int start = cursor, deleted = 0;
            boolean hasOld = false;
            for (int j = i; j < end; j++) {
                Line line = lines.get(j);
                if (line.oldNumber() != null) { if (!hasOld) start = line.oldNumber(); hasOld = true; deleted++; }
            }
            if (!hasOld) {
                start = base.size() + 1;
                if (end < lines.size() && lines.get(end).block() == first.block() && lines.get(end).oldNumber() != null) start = lines.get(end).oldNumber();
            }
            int added = 0;
            for (int j = i; j < end; j++) {
                Line line = lines.get(j); boolean apply = stage == chosen.contains(line.id());
                if (line.type().equals("+")) {
                    int anchor = start + Math.min(added++, deleted);
                    if (apply) additions.computeIfAbsent(anchor, ignored -> new ArrayList<>()).add(line.text() + (line.newline() ? "\n" : ""));
                } else if (apply) deletions.add(line.oldNumber());
            }
            cursor = start + deleted; i = end;
        }
        StringBuilder result = new StringBuilder();
        for (int number = 1; number <= base.size() + 1; number++) {
            for (String added : additions.getOrDefault(number, List.of())) appendLine(result, added);
            if (number <= base.size() && !deletions.contains(number)) appendLine(result, base.get(number - 1));
        }
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }
    private void appendLine(StringBuilder result, String line) {
        if (!line.isEmpty() && !result.isEmpty() && result.charAt(result.length() - 1) != '\n')
            throw new GitProblem(400, "This selection would join lines at end of file. Select the complete replacement block to preserve its newline change.");
        result.append(line);
    }
    private static List<String> splitLines(String content) {
        List<String> lines = new ArrayList<>(); int begin = 0;
        for (int i = 0; i < content.length(); i++) if (content.charAt(i) == '\n') { lines.add(content.substring(begin, i + 1)); begin = i + 1; }
        if (begin < content.length()) lines.add(content.substring(begin));
        return lines;
    }
    private void updateIndex(Resolved repository, String version, Consumer<Map<String, String>> mutation) {
        Path index = repository.gitDir().resolve("index"), lock = repository.gitDir().resolve("index.lock");
        Path temporary = repository.gitDir().resolve("mvnbuilder-index-" + UUID.randomUUID()); boolean owned = false;
        try {
            Files.createFile(lock); owned = true;
            expect(repository, version);
            if (Files.exists(index)) Files.copy(index, temporary);
            Map<String, String> env = Map.of("GIT_INDEX_FILE", temporary.toString());
            if (!Files.exists(temporary)) git.run(repository.root(), null, env, false, "read-tree", "--empty").require();
            mutation.accept(env);
            expect(repository, version);
            Files.copy(temporary, lock, StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(lock, index, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { throw new GitProblem(422, "Filesystem cannot publish a Git index atomically."); }
            owned = false;
        } catch (FileAlreadyExistsException e) { throw new GitProblem(409, "Another Git operation holds index.lock. Wait for it to finish and refresh.");
        } catch (IOException e) { throw new GitProblem(422, "Cannot update Git staging area: " + e.getMessage());
        } finally { try { Files.deleteIfExists(temporary); Files.deleteIfExists(Path.of(temporary + ".lock")); if (owned) Files.deleteIfExists(lock); } catch (IOException ignored) { } }
    }
    public static String fileHash(Path file) {
        try {
            if (Files.isSymbolicLink(file)) return hash(Files.readSymbolicLink(file).toString().getBytes(StandardCharsets.UTF_8));
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return "missing";
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream stream = Files.newInputStream(file)) { byte[] buffer = new byte[8192]; int read; while ((read = stream.read(buffer)) != -1) digest.update(buffer, 0, read); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) { throw new GitProblem(422, "Cannot inspect file: " + file); }
    }
}
