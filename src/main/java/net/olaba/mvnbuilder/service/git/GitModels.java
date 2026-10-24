package net.olaba.mvnbuilder.service.git;

import java.nio.file.Path;
import java.util.List;

public final class GitModels {
    private GitModels() { }
    public record Repository(String id, String name, String path) { }
    public record Resolved(Repository descriptor, Path root, Path gitDir, Path commonDir) { }
    public record Discovery(List<Repository> repositories, List<String> warnings, boolean complete, long visited) { }
    public record FileChange(String path, String oldPath, String index, String working, String kind) { }
    public record Status(String branch, String upstream, Integer ahead, Integer behind, boolean unborn,
                         String version, String stagedVersion, List<FileChange> files) { }
    public record Line(String id, String type, Integer oldNumber, Integer newNumber, String text, boolean newline, int block) { }
    public record Diff(String path, String oldPath, String area, String version, String kind, String reason,
                       boolean partial, List<Line> lines) { }
    public record Selection(String path, String area, String version, List<String> lines, boolean all) { }
    public record Commit(String message, String version) { }
    public record CommitFile(String path, String area) { }
    public record CommitTarget(String id, String version, List<CommitFile> files) { }
    public record SelectedCommit(String message, List<CommitTarget> targets) { }
    public record Target(String id, String version) { }
    public record Action(String action, List<Target> targets, String path, boolean includeUntracked, String confirmation) { }
    public record Outcome(String repositoryId, String state, String message) { }
    public record Operation(String id, String state, List<Outcome> outcomes, Discovery discovery) { }
}
