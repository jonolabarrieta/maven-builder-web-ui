package net.olaba.mvnbuilder.controller;

import net.olaba.mvnbuilder.service.*;
import net.olaba.mvnbuilder.repository.JavaInstallationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/parent-workspaces")
public class ParentWorkspaceController {
    private final WorkspaceGroupService groups;
    private final SystemSettingService settings;
    private final JavaInstallationRepository javaInstallations;
    private final UpdateService updates;
    private final WorkspaceService workspaces;

    @GetMapping
    public String index(Model model) {
        var parents = groups.parents();
        model.addAttribute("workspaces", parents); model.addAttribute("parentTab", true);
        var consumers = new java.util.HashMap<Long, java.util.List<net.olaba.mvnbuilder.entities.Workspace>>();
        parents.forEach(p -> consumers.put(p.getId(), groups.consumers(p.getId())));
        model.addAttribute("parentConsumers", consumers);
        model.addAttribute("favoritePath", settings.getSettings().getFavoritePath());
        model.addAttribute("javaInstallations", javaInstallations.findAll());
        model.addAttribute("currentVersion", updates.getCurrentVersion());
        return "index";
    }
    @PostMapping
    public String create(@RequestParam String name, @RequestParam(required=false) String basePath,
            @RequestParam(defaultValue="false") boolean skipScan, @RequestParam(required=false) Long javaInstallationId) {
        var parent = groups.createParent(name, basePath, skipScan);
        if (javaInstallationId != null) javaInstallations.findById(javaInstallationId).ifPresent(parent::setJavaInstallation);
        workspaces.saveWorkspace(parent);
        return "redirect:/workspaces/" + parent.getId();
    }
}
