package net.olaba.mvnbuilder.controller;

import lombok.RequiredArgsConstructor;
import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.service.BuildHistoryService;
import net.olaba.mvnbuilder.service.UpdateService;
import net.olaba.mvnbuilder.service.WorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Renders the per-workspace build history screen.
 */
@Controller
@RequestMapping("/workspaces/{id}/build-history")
@RequiredArgsConstructor
public class BuildHistoryController {

    private final WorkspaceService workspaceService;
    private final BuildHistoryService buildHistoryService;
    private final UpdateService updateService;

    /**
     * Exposes the running application version to the page.
     *
     * @return the current version
     */
    @ModelAttribute("currentVersion")
    public String currentVersion() {
        return updateService.getCurrentVersion();
    }

    /**
     * Renders the build history page for a workspace.
     *
     * @param id    the workspace ID
     * @param page  zero-based page index
     * @param model the UI model
     * @return the build history view name
     */
    @GetMapping
    public String history(final @PathVariable Long id,
            final @RequestParam(name = "page", defaultValue = "0") int page,
            final Model model) {
        final Workspace workspace = workspaceService.getWorkspace(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));
        model.addAttribute("workspace", workspace);
        model.addAttribute("history", buildHistoryService.load(id, page));
        return "build-history";
    }
}
