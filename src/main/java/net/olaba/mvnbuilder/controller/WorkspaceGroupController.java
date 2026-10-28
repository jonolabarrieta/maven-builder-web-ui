package net.olaba.mvnbuilder.controller;

import net.olaba.mvnbuilder.entities.*;
import net.olaba.mvnbuilder.service.*;
import net.olaba.mvnbuilder.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.io.File;
import java.util.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/workspaces/{workspaceId}/groups")
public class WorkspaceGroupController {
    private final WorkspaceGroupService groups;
    private final BuildService builds;
    private final GitService git;
    private final NotificationService notifications;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private BuildGuardService buildGuard;

    private String back(Long id) { return "redirect:/workspaces/" + id; }

    @PostMapping
    public String legacyCreate(@PathVariable Long workspaceId, @RequestParam String name,
            @RequestParam(required=false) Long parentId) {
        groups.create(workspaceId, name, parentId); return back(workspaceId);
    }
    /** Creates a visible group from the project selection made in the dialog. */
    @PostMapping("/create")
    public String create(@PathVariable Long workspaceId, @RequestParam String name,
            @RequestParam(required=false) List<Long> projectIds) {
        groups.createWithProjects(workspaceId, name, projectIds); return back(workspaceId);
    }
    /** Adds a parent workspace without asking for an internal label. */
    @PostMapping("/import-parent")
    public String importParent(@PathVariable Long workspaceId, @RequestParam Long parentId) {
        groups.create(workspaceId, null, parentId); return back(workspaceId);
    }
    @PostMapping("/{groupId}/add-projects")
    public String addProjects(@PathVariable Long workspaceId, @PathVariable Long groupId,
            @RequestParam(required=false) List<Long> projectIds) {
        groups.addMembers(workspaceId, groupId, projectIds); return back(workspaceId);
    }
    @PostMapping("/{groupId}/rename")
    public String rename(@PathVariable Long workspaceId, @PathVariable Long groupId, @RequestParam String name) {
        groups.rename(workspaceId, groupId, name); return back(workspaceId);
    }
    @PostMapping("/{groupId}/delete")
    public String remove(@PathVariable Long workspaceId, @PathVariable Long groupId,
            @RequestParam(defaultValue="false") boolean deleteProjects) {
        groups.remove(workspaceId, groupId, deleteProjects); return back(workspaceId);
    }
    @PostMapping("/move-project")
    public String move(@PathVariable Long workspaceId, @RequestParam Long projectId, @RequestParam(required=false) Long groupId) {
        groups.move(workspaceId, projectId, groupId); return back(workspaceId);
    }
    @PostMapping("/order") @ResponseBody
    public ResponseEntity<Void> order(@PathVariable Long workspaceId, @RequestBody List<String> keys) {
        groups.reorderBlocks(workspaceId, keys); return ResponseEntity.ok().build();
    }
    @PostMapping("/{groupId}/order") @ResponseBody
    public ResponseEntity<Void> memberOrder(@PathVariable Long workspaceId, @PathVariable Long groupId, @RequestBody List<Long> ids) {
        groups.reorderMembers(workspaceId, groupId, ids); return ResponseEntity.ok().build();
    }
    @PostMapping("/{groupId}/refresh")
    public Object refresh(@PathVariable Long workspaceId, @PathVariable Long groupId,
            @RequestHeader(value="X-Group-Refresh", defaultValue="false") boolean ajax) {
        groups.refresh(workspaceId, groupId); return ajax ? ResponseEntity.ok("Group refreshed.") : back(workspaceId);
    }
    @PostMapping("/{groupId}/action") @ResponseBody
    public ResponseEntity<String> action(@PathVariable Long workspaceId, @PathVariable Long groupId,
            @RequestParam String action, @RequestParam(required=false) List<Long> projectIds,
            @RequestParam(defaultValue="false") boolean includeUntracked,
            @RequestParam(defaultValue="false") boolean createNew, @RequestParam(required=false) String branch,
            @RequestParam(required=false) String runGuard) {
        List<MavenProject> targets = groups.scope(workspaceId, groupId, projectIds, false);
        if (Set.of("fetch","pull","checkout","stash","discard","unstage").contains(action) && BuildService.gitTargets(targets).isEmpty())
            return ResponseEntity.ok("No eligible Git repositories in this scope. Disabled or unavailable projects were omitted.");
        switch (action) {
            case "build" -> BuildGuardService.build(buildGuard, workspaceId, groups.workspace(workspaceId).getName(), targets, runGuard,
                () -> builds.buildProjectsSequentially(targets));
            case "fetch" -> builds.bulkGitFetch(targets);
            case "pull" -> builds.bulkGitPull(targets);
            case "stash" -> builds.bulkGitStash(targets, includeUntracked);
            case "discard" -> builds.bulkGitDiscard(targets);
            case "unstage" -> builds.bulkGitUnstage(targets);
            case "enable", "disable" -> groups.setEnabled(workspaceId, groupId, projectIds, action.equals("enable"));
            case "checkout" -> {
                if (branch == null || branch.isBlank()) return ResponseEntity.badRequest().body("Choose a Git reference.");
                List<String> failed = new ArrayList<>(), succeeded = new ArrayList<>();
                for (MavenProject p : BuildService.gitTargets(targets)) {
                    try {
                        if (createNew) git.createBranch(new File(p.getAbsolutePath()), branch);
                        else git.checkoutBranch(new File(p.getAbsolutePath()), branch);
                        succeeded.add(p.getDisplayName());
                    } catch (RuntimeException e) { failed.add(p.getDisplayName() + ": " + e.getMessage()); }
                }
                Workspace owner = groups.workspace(workspaceId);
                notifications.publish(ActionSummary.builder().withAction("git-checkout").withSuccess(failed.isEmpty())
                    .withFailedProjects(failed).withSucceededProjects(succeeded).build(), workspaceId, owner.getName());
                if (!failed.isEmpty()) return ResponseEntity.badRequest().body(String.join("\n", failed));
            }
            default -> { return ResponseEntity.badRequest().body("Unknown group action."); }
        }
        return ResponseEntity.ok("Action accepted.");
    }

    /** Dialog with all project roots; grouped and imported rows are shown disabled. */
    @GetMapping("/new-modal")
    public String newModal(@PathVariable Long workspaceId, Model model) {
        model.addAttribute("workspace", groups.workspace(workspaceId));
        model.addAttribute("dialogProjects", groups.composition(workspaceId).roots());
        return "fragments/workspace-group-modal :: group-modal-content";
    }
    /** Dialog used from a group header to add more projects to that group. */
    @GetMapping("/{groupId}/edit-modal")
    public String editModal(@PathVariable Long workspaceId, @PathVariable Long groupId, Model model) {
        model.addAttribute("workspace", groups.workspace(workspaceId));
        model.addAttribute("group", groups.group(workspaceId, groupId));
        model.addAttribute("dialogProjects", groups.composition(workspaceId).roots());
        return "fragments/workspace-group-modal :: group-modal-content";
    }
    /** Dialog listing linked and still available parent workspaces. */
    @GetMapping("/parent-workspaces-modal")
    public String parentModal(@PathVariable Long workspaceId, Model model) {
        model.addAttribute("workspace", groups.workspace(workspaceId));
        model.addAttribute("importedParents", groups.importedGroups(workspaceId));
        model.addAttribute("availableParents", groups.availableParents(workspaceId));
        return "fragments/workspace-group-modal :: parent-workspaces-modal-content";
    }
}
