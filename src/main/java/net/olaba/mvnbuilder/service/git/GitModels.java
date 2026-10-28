package net.olaba.mvnbuilder.service.git;

import java.nio.file.Path;
import java.util.List;

public final class GitModels {
    private GitModels() { }
    /** {@code name} is the working-tree folder name; {@code displayName} adds parent folders only to tell equal names apart. */
    public record Repository(String id, String name, String displayName, String absolutePath) { }
    public record Resolved(Repository descriptor, Path root, Path gitDir, Path commonDir) { }
    /** {@code visited} counts the registered projects that were resolved. */
    public record Discovery(List<Repository> repositories, List<String> warnings, boolean complete, long visited) { }
    public record FileChange(String path, String oldPath, String index, String working, String kind) { }
    public record Status(String branch, String upstream, Integer ahead, Integer behind, boolean unborn,
                         String version, String stagedVersion, List<FileChange> files) { }
    public record Line(String id, String type, Integer oldNumber, Integer newNumber, String text, boolean newline, int block) { }
    public record Diff(String path, String oldPath, String area, String version, String kind, String reason,
                       boolean partial, List<Line> lines) { }
    public record Selection(String path, String area, String version, List<String> lines, boolean all) { }
    /** {@code area} is "changes" (working-tree content) or "staged" (index content). */
    public record CommitFile(String path, String area) { }
    public record CommitTarget(String id, String version, List<CommitFile> files) { }
    /** Commits only the marked files of each target; {@code push} pushes each new commit within the same operation (Commit & Push). */
    public record SelectedCommit(String message, List<CommitTarget> targets, boolean push) {
        public SelectedCommit(String message, List<CommitTarget> targets) { this(message, targets, false); }
    }
    public record Target(String id, String version) { }
    /** {@code remote} names the destination for publish; other actions ignore it. */
    /** {@code remote} names the destination for publish; {@code stash}/{@code stashSha} identify the stash a stash action targets. */
    public record Action(String action, List<Target> targets, String path, boolean includeUntracked, String confirmation, String remote, String stash, String stashSha) {
        public Action(String action, List<Target> targets, String path, boolean includeUntracked, String confirmation) { this(action, targets, path, includeUntracked, confirmation, null, null, null); }
        public Action(String action, List<Target> targets, String path, boolean includeUntracked, String confirmation, String remote) { this(action, targets, path, includeUntracked, confirmation, remote, null, null); }
    }
    /** {@code time} is epoch seconds; {@code branch} is where the stash was created, when Git recorded it. */
    public record StashEntry(String ref, String sha, String message, String branch, long time) { }
    public record Outcome(String repositoryId, String state, String message) { }
    /** ahead/behind are null without an upstream; {@code gone} marks an upstream that no longer exists after a fetch. */
    public record SyncBranch(String name, boolean current, String upstream, Integer ahead, Integer behind, boolean gone) { }
    /** {@code lastFetch} is epoch milliseconds of the last recorded fetch, or null when none is known; the current branch is listed first. */
    public record SyncRepository(String id, String name, String displayName, String absolutePath, String branch, int changes, Long lastFetch, List<SyncBranch> branches, String error) { }
    public record SyncSummary(List<SyncRepository> repositories) { }
    /** {@code pushed} is true when the commit is contained in the branch's upstream; false when not, or when there is no upstream. */
    public record HistoryCommit(String sha, String shortSha, String subject, String author, long time, boolean pushed) { }
    public record HistoryPage(List<HistoryCommit> commits, boolean more, boolean upstream) { }
    public record CommitFileChange(String status, String path, String oldPath) { }
    /** {@code type} is LOCAL, REMOTE or TAG; {@code ref} is the full reference used for checkout. */
    public record ReferenceOption(String type, String ref, String name) { }
    public record References(List<ReferenceOption> local, List<ReferenceOption> remote, List<ReferenceOption> tags, String current) { }
    public record Checkout(String reference, String type) { }
    public record NewBranch(String name) { }
    /** Editable working-tree text; {@code content} uses LF breaks and {@code hash} identifies the bytes on disk. */
    public record FileContent(String path, String content, String hash, String eol, boolean bom, boolean finalNewline, boolean editable, String reason) { }
    public record FileEdit(String path, String content, String hash) { }
    public record Operation(String id, String state, List<Outcome> outcomes, Discovery discovery) { }
}
