package net.olaba.mvnbuilder.controller;

import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.service.WorkspaceService;
import net.olaba.mvnbuilder.service.git.*;
import static net.olaba.mvnbuilder.service.git.GitModels.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Controller
@RequestMapping("/workspaces/{workspaceId}/git")
public class WorkspaceGitController {
    private final WorkspaceService workspaces;
    private final WorkspaceGitDiscoveryService discovery;
    private final GitChangeService changes;
    private final GitRepositoryActionService actions;
    public WorkspaceGitController(WorkspaceService workspaces, WorkspaceGitDiscoveryService discovery, GitChangeService changes, GitRepositoryActionService actions) {
        this.workspaces = workspaces; this.discovery = discovery; this.changes = changes; this.actions = actions;
    }
    private Workspace workspace(Long id) { return workspaces.getWorkspace(id).orElseThrow(() -> new GitProblem(404, "Workspace was not found.")); }
    @GetMapping({"", "/"}) public String page(@PathVariable Long workspaceId, Model model) { model.addAttribute("workspace", workspace(workspaceId)); return "workspace-git"; }
    @GetMapping("/repositories") @ResponseBody public Operation repositories(@PathVariable Long workspaceId) { return actions.discover(workspace(workspaceId)); }
    @GetMapping("/repositories/{repositoryId}/status") @ResponseBody public Status status(@PathVariable Long workspaceId, @PathVariable String repositoryId) {
        return changes.status(discovery.repository(workspace(workspaceId), repositoryId));
    }
    @GetMapping("/repositories/{repositoryId}/diff") @ResponseBody public Diff diff(@PathVariable Long workspaceId, @PathVariable String repositoryId, @RequestParam String path, @RequestParam String area) {
        return changes.diff(discovery.repository(workspace(workspaceId), repositoryId), path, area);
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
    @PostMapping("/actions") @ResponseBody public Operation action(@PathVariable Long workspaceId, @RequestBody Action request) { return actions.actions(workspace(workspaceId), request); }
    @GetMapping("/operations/{operationId}") @ResponseBody public Operation operation(@PathVariable Long workspaceId, @PathVariable String operationId) { return actions.operation(workspace(workspaceId), operationId); }
    @ExceptionHandler(GitProblem.class) @ResponseBody public ResponseEntity<Map<String, String>> problem(GitProblem error) {
        return ResponseEntity.status(error.status()).body(Map.of("message", error.getMessage()));
    }
    @ExceptionHandler({IllegalArgumentException.class, org.springframework.http.converter.HttpMessageNotReadableException.class}) @ResponseBody
    public ResponseEntity<Map<String, String>> badRequest(Exception error) { return ResponseEntity.badRequest().body(Map.of("message", "Invalid Git request. Refresh the page and try again.")); }
}
