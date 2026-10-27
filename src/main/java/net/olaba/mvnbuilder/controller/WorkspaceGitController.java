package net.olaba.mvnbuilder.controller;

import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.service.WorkspaceService;
import net.olaba.mvnbuilder.service.git.*;
import static net.olaba.mvnbuilder.service.git.GitModels.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/workspaces/{workspaceId}/git")
public class WorkspaceGitController {
    private final WorkspaceService workspaces;
    private final WorkspaceGitDiscoveryService discovery;
    private final GitChangeService changes;
    private final GitRepositoryActionService actions;
    private final GitSyncService sync;
    private final GitHistoryService history;
    private final GitBranchService branches;
    private final net.olaba.mvnbuilder.service.DesktopLauncherService launcher;
    private final GitFileEditService files;
    public WorkspaceGitController(WorkspaceService workspaces, WorkspaceGitDiscoveryService discovery, GitChangeService changes, GitRepositoryActionService actions,
            GitSyncService sync, GitHistoryService history, GitBranchService branches, net.olaba.mvnbuilder.service.DesktopLauncherService launcher, GitFileEditService files) {
        this.workspaces = workspaces; this.discovery = discovery; this.changes = changes; this.actions = actions; this.sync = sync; this.history = history; this.branches = branches;
        this.launcher = launcher; this.files = files;
    }
    @org.springframework.beans.factory.annotation.Value("${workspace.git.refresh-interval-seconds:60}") private int refreshInterval = 60;
    @org.springframework.beans.factory.annotation.Value("${workspace.git.highlight-max-lines:5000}") private int highlightMaxLines = 5000;
    @org.springframework.beans.factory.annotation.Value("${workspace.git.stale-fetch-hours:24}") private int staleFetchHours = 24;
    private Workspace workspace(Long id) { return workspaces.getWorkspace(id).orElseThrow(() -> new GitProblem(404, "Workspace was not found.")); }
    @GetMapping({"", "/"}) public String page(@PathVariable Long workspaceId, Model model) {
        model.addAttribute("workspace", workspace(workspaceId)); model.addAttribute("gitRefreshInterval", refreshInterval);
        model.addAttribute("gitHighlightMaxLines", highlightMaxLines); model.addAttribute("gitStaleFetchHours", staleFetchHours); return "workspace-git";
    }
    @GetMapping("/repositories") @ResponseBody public Operation repositories(@PathVariable Long workspaceId, @RequestParam(defaultValue = "false") boolean refresh) {
        return actions.discover(workspace(workspaceId), refresh);
    }
    /** {@code full=true} returns the content-identified snapshot that destructive actions and selected commits submit. */
    @GetMapping("/repositories/{repositoryId}/status") @ResponseBody public Status status(@PathVariable Long workspaceId, @PathVariable String repositoryId, @RequestParam(defaultValue = "false") boolean full) {
        return changes.status(discovery.repository(workspace(workspaceId), repositoryId), full);
    }
    @GetMapping("/repositories/{repositoryId}/untracked") @ResponseBody public List<FileChange> untracked(@PathVariable Long workspaceId, @PathVariable String repositoryId, @RequestParam String dir) {
        return changes.untracked(discovery.repository(workspace(workspaceId), repositoryId), dir);
    }
    /** {@code context=all} returns the whole file; changed lines keep the identities of the default comparison. */
    @GetMapping("/repositories/{repositoryId}/diff") @ResponseBody public Diff diff(@PathVariable Long workspaceId, @PathVariable String repositoryId, @RequestParam String path, @RequestParam String area,
            @RequestParam(defaultValue = "default") String context) {
        return changes.diff(discovery.repository(workspace(workspaceId), repositoryId), path, area, "all".equals(context));
    }
    @PostMapping("/repositories/{repositoryId}/stage") @ResponseBody public Status stage(@PathVariable Long workspaceId, @PathVariable String repositoryId, @RequestBody Selection selection) {
        var repository = discovery.repository(workspace(workspaceId), repositoryId); changes.select(repository, selection, true); return changes.status(repository);
    }
    @PostMapping("/repositories/{repositoryId}/unstage") @ResponseBody public Status unstage(@PathVariable Long workspaceId, @PathVariable String repositoryId, @RequestBody Selection selection) {
        var repository = discovery.repository(workspace(workspaceId), repositoryId); changes.select(repository, selection, false); return changes.status(repository);
    }
    @PostMapping("/repositories/{repositoryId}/commit") @ResponseBody public Operation commit(@PathVariable Long workspaceId, @PathVariable String repositoryId, @RequestBody Commit request) {
        return actions.commit(workspace(workspaceId), repositoryId, request);
    }
    @PostMapping("/commits") @ResponseBody public Operation commits(@PathVariable Long workspaceId, @RequestBody SelectedCommit request) {
        return actions.commits(workspace(workspaceId), request);
    }
    @GetMapping("/repositories/{repositoryId}/stashes") @ResponseBody public List<StashEntry> stashes(@PathVariable Long workspaceId, @PathVariable String repositoryId) {
        return actions.stashes(discovery.repository(workspace(workspaceId), repositoryId));
    }
    @GetMapping("/repositories/{repositoryId}/remotes") @ResponseBody public List<String> remotes(@PathVariable Long workspaceId, @PathVariable String repositoryId) {
        return actions.remotes(discovery.repository(workspace(workspaceId), repositoryId));
    }
    @GetMapping("/repositories/{repositoryId}/references") @ResponseBody public References references(@PathVariable Long workspaceId, @PathVariable String repositoryId) {
        return branches.list(discovery.repository(workspace(workspaceId), repositoryId));
    }
    @PostMapping("/repositories/{repositoryId}/checkout") @ResponseBody public Map<String, String> checkout(@PathVariable Long workspaceId, @PathVariable String repositoryId, @RequestBody Checkout request) {
        Workspace workspace = workspace(workspaceId);
        return Map.of("current", branches.checkout(workspace, discovery.repository(workspace, repositoryId), request.reference(), request.type()));
    }
    @PostMapping("/repositories/{repositoryId}/branches") @ResponseBody public Map<String, String> createBranch(@PathVariable Long workspaceId, @PathVariable String repositoryId, @RequestBody NewBranch request) {
        Workspace workspace = workspace(workspaceId);
        return Map.of("current", branches.create(workspace, discovery.repository(workspace, repositoryId), request.name()));
    }
    /** Opens the repository root in VS Code using the same launcher as the project shortcuts. */
    @PostMapping("/repositories/{repositoryId}/open-editor") @ResponseBody public Map<String, String> openEditor(@PathVariable Long workspaceId, @PathVariable String repositoryId) {
        var repository = discovery.repository(workspace(workspaceId), repositoryId);
        try { launcher.openVsCode(repository.root().toFile()); }
        catch (java.io.IOException e) { throw new GitProblem(422, "Visual Studio Code could not be started. Check that the code command is available."); }
        return Map.of("opened", repository.descriptor().path());
    }
    @GetMapping("/repositories/{repositoryId}/file") @ResponseBody public FileContent file(@PathVariable Long workspaceId, @PathVariable String repositoryId, @RequestParam String path) {
        return files.read(discovery.repository(workspace(workspaceId), repositoryId), path);
    }
    @PutMapping("/repositories/{repositoryId}/file") @ResponseBody public FileContent saveFile(@PathVariable Long workspaceId, @PathVariable String repositoryId, @RequestBody FileEdit request) {
        return files.write(discovery.repository(workspace(workspaceId), repositoryId), request.path(), request.content(), request.hash());
    }
    @GetMapping("/repositories/{repositoryId}/amend") @ResponseBody public AmendState amend(@PathVariable Long workspaceId, @PathVariable String repositoryId) {
        return history.amendState(discovery.repository(workspace(workspaceId), repositoryId));
    }
    @GetMapping("/repositories/{repositoryId}/log") @ResponseBody public HistoryPage log(@PathVariable Long workspaceId, @PathVariable String repositoryId,
            @RequestParam(defaultValue = "0") int skip, @RequestParam(defaultValue = "30") int limit) {
        return history.log(discovery.repository(workspace(workspaceId), repositoryId), skip, limit);
    }
    @GetMapping("/repositories/{repositoryId}/commits/{sha}/files") @ResponseBody public List<CommitFileChange> commitFiles(@PathVariable Long workspaceId, @PathVariable String repositoryId, @PathVariable String sha) {
        return history.files(discovery.repository(workspace(workspaceId), repositoryId), sha);
    }
    @GetMapping("/repositories/{repositoryId}/commits/{sha}/diff") @ResponseBody public Diff commitDiff(@PathVariable Long workspaceId, @PathVariable String repositoryId, @PathVariable String sha,
            @RequestParam String path, @RequestParam(required = false) String oldPath) {
        return changes.commitDiff(discovery.repository(workspace(workspaceId), repositoryId), sha, path, oldPath);
    }
    @GetMapping("/sync") @ResponseBody public SyncSummary sync(@PathVariable Long workspaceId) { return sync.summary(workspace(workspaceId)); }
    @PostMapping("/actions") @ResponseBody public Operation action(@PathVariable Long workspaceId, @RequestBody Action request) { return actions.actions(workspace(workspaceId), request); }
    @GetMapping("/operations/{operationId}") @ResponseBody public Operation operation(@PathVariable Long workspaceId, @PathVariable String operationId) { return actions.operation(workspace(workspaceId), operationId); }
    @ExceptionHandler(GitProblem.class) @ResponseBody public ResponseEntity<Map<String, String>> problem(GitProblem error) {
        return ResponseEntity.status(error.status()).body(error.code() == null ? Map.of("message", error.getMessage()) : Map.of("message", error.getMessage(), "code", error.code()));
    }
    @ExceptionHandler({IllegalArgumentException.class, org.springframework.http.converter.HttpMessageNotReadableException.class}) @ResponseBody
    public ResponseEntity<Map<String, String>> badRequest(Exception error) { return ResponseEntity.badRequest().body(Map.of("message", "Invalid Git request. Refresh the page and try again.")); }
}
