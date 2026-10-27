package net.olaba.mvnbuilder.service.git;

import org.springframework.stereotype.Service;
import static net.olaba.mvnbuilder.service.git.GitModels.*;
import java.util.*;
import java.util.regex.Pattern;

/** Read-only history of the current branch: recent commits, their files and per-file comparisons. */
@Service
public class GitHistoryService {
    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{7,64}");
    private final GitCommandRunner git;
    private final GitChangeService changes;
    public GitHistoryService(GitCommandRunner git, GitChangeService changes) { this.git = git; this.changes = changes; }
    /** Rejects anything but a hexadecimal object name, so user input can never become a revision expression or option. */
    public static String commit(String sha) {
        if (sha == null || !COMMIT.matcher(sha).matches()) throw new GitProblem(400, "Choose a commit by its hexadecimal identifier.");
        return sha;
    }
    public HistoryPage log(Resolved repository, int skip, int limit) {
        if (changes.head(repository).equals("unborn")) return new HistoryPage(List.of(), false, false);
        int size = Math.max(1, Math.min(limit, 200));
        String output = git.run(repository.root(), "log", "-z", "--format=%H%x1f%h%x1f%s%x1f%an%x1f%at", "--skip=" + Math.max(0, skip), "-n", String.valueOf(size + 1), "HEAD").require().text();
        // Commits on HEAD but not on its upstream are unpushed; without an upstream nothing is known to be pushed.
        var upstream = git.run(repository.root(), "rev-list", "@{upstream}..HEAD");
        boolean tracked = upstream.code() == 0;
        Set<String> unpushed = tracked ? new HashSet<>(Arrays.asList(upstream.text().split("\n"))) : Set.of();
        List<HistoryCommit> commits = new ArrayList<>();
        for (String record : output.split("\0")) {
            if (record.isBlank()) continue;
            String[] fields = record.strip().split("\u001f", -1);
            if (fields.length < 5) continue;
            commits.add(new HistoryCommit(fields[0], fields[1], fields[2], fields[3], Long.parseLong(fields[4]), tracked && !unpushed.contains(fields[0])));
        }
        boolean more = commits.size() > size;
        return new HistoryPage(List.copyOf(more ? commits.subList(0, size) : commits), more, tracked);
    }
    /** Amending is limited to an unpushed last commit outside merges, rebases, cherry-picks and reverts. */
    public AmendState amendState(Resolved repository) {
        String head = changes.head(repository);
        if (head.equals("unborn")) return new AmendState(null, "", false, "There is no commit to amend yet.");
        String message = git.run(repository.root(), "log", "-1", "--format=%B", head).require().text().stripTrailing();
        for (String marker : List.of("MERGE_HEAD", "CHERRY_PICK_HEAD", "REVERT_HEAD", "rebase-merge", "rebase-apply"))
            if (java.nio.file.Files.exists(repository.gitDir().resolve(marker))) return new AmendState(head, message, false, "Finish the current merge, rebase, cherry-pick or revert first.");
        var upstream = git.run(repository.root(), "rev-parse", "--verify", "--quiet", "@{upstream}");
        if (upstream.code() == 0 && git.run(repository.root(), "merge-base", "--is-ancestor", head, upstream.text().strip()).code() == 0)
            return new AmendState(head, message, false, "The last commit is already pushed; amending it would require a force push.");
        return new AmendState(head, message, true, null);
    }
    public List<CommitFileChange> files(Resolved repository, String sha) {
        String output = git.run(repository.root(), "diff-tree", "--no-commit-id", "--name-status", "-r", "-z", "-M", "--root", commit(sha)).require().text();
        List<CommitFileChange> files = new ArrayList<>();
        String[] fields = output.split("\0");
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].isEmpty()) continue;
            String status = fields[i];
            if (status.startsWith("R") || status.startsWith("C")) { files.add(new CommitFileChange(status.substring(0, 1), fields[i + 2], fields[i + 1])); i += 2; }
            else { files.add(new CommitFileChange(status.substring(0, 1), fields[i + 1], null)); i++; }
        }
        return List.copyOf(files);
    }
}
