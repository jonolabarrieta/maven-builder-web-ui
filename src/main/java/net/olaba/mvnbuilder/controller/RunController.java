package net.olaba.mvnbuilder.controller;

import lombok.RequiredArgsConstructor;
import net.olaba.mvnbuilder.entities.RunEntry;
import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.service.RunConfigurationService;
import net.olaba.mvnbuilder.service.RunProcessRegistry;
import net.olaba.mvnbuilder.service.WorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** RUN entries of a workspace: configuration, launch selection and process control. */
@Controller
@RequiredArgsConstructor
@RequestMapping("/workspaces/{workspaceId}/run")
public class RunController {
    private static final String FRAGMENTS = "fragments/run";

    private final RunConfigurationService configurations;
    private final RunProcessRegistry processes;
    private final WorkspaceService workspaces;

    /** Result of a launch request. */
    public record StartResult(List<Long> started, List<String> errors) { }

    // ---- Configuration -------------------------------------------------------------------

    /** Renders the RUN configuration editor with an empty "new entry" form. */
    @GetMapping("/entries")
    public String entries(@PathVariable Long workspaceId, Model model) {
        return config(workspaceId, model, null, emptyForm(), Map.of());
    }

    /** Renders the editor with the form filled for one entry. */
    @GetMapping("/entries/{entryId}/edit")
    public String edit(@PathVariable Long workspaceId, @PathVariable Long entryId, Model model) {
        RunEntry entry = configurations.entry(workspaceId, entryId);
        RunConfigurationService.Form form = new RunConfigurationService.Form(entry.getName(), entry.getProjectId(),
            entry.getWorkingDirectory(), entry.getCommand(),
            entry.getEnv().stream().map(v -> v.getName()).toList(), entry.getEnv().stream().map(v -> v.getValue()).toList(),
            entry.isUseWorkspaceJava());
        return config(workspaceId, model, entryId, form, Map.of());
    }

    @PostMapping("/entries")
    public String create(@PathVariable Long workspaceId, @ModelAttribute EntryParams params, Model model) {
        try {
            configurations.create(workspaceId, params.form());
            return config(workspaceId, model, null, emptyForm(), Map.of());
        } catch (RunConfigurationService.ValidationException invalid) {
            return config(workspaceId, model, null, params.form(), invalid.getErrors());
        }
    }

    @PutMapping("/entries/{entryId}")
    public String update(@PathVariable Long workspaceId, @PathVariable Long entryId, @ModelAttribute EntryParams params, Model model) {
        try {
            configurations.update(workspaceId, entryId, params.form());
            return config(workspaceId, model, null, emptyForm(), Map.of());
        } catch (RunConfigurationService.ValidationException invalid) {
            return config(workspaceId, model, entryId, params.form(), invalid.getErrors());
        }
    }

    @PostMapping("/entries/{entryId}/duplicate")
    public String duplicate(@PathVariable Long workspaceId, @PathVariable Long entryId, Model model) {
        configurations.duplicate(workspaceId, entryId);
        return config(workspaceId, model, null, emptyForm(), Map.of());
    }

    @DeleteMapping("/entries/{entryId}")
    public String delete(@PathVariable Long workspaceId, @PathVariable Long entryId, Model model) {
        configurations.entry(workspaceId, entryId);
        processes.stop(workspaceId, entryId);
        processes.dismiss(workspaceId, entryId);
        configurations.delete(workspaceId, entryId);
        return config(workspaceId, model, null, emptyForm(), Map.of());
    }

    @PostMapping("/entries/order")
    @ResponseBody
    public ResponseEntity<Void> order(@PathVariable Long workspaceId, @RequestBody List<Long> ids) {
        configurations.reorder(workspaceId, ids);
        return ResponseEntity.ok().build();
    }

    // ---- Launch --------------------------------------------------------------------------

    /** Renders the launch selection modal. */
    @GetMapping("/modal")
    public String modal(@PathVariable Long workspaceId, Model model) {
        model.addAttribute("workspace", workspace(workspaceId));
        model.addAttribute("entries", configurations.views(workspaceId));
        model.addAttribute("running", processes.runningEntries(workspaceId));
        return FRAGMENTS + " :: run-launch-content";
    }

    /** Starts the selected entries at the same time and remembers the selection. */
    @PostMapping(value = "/start", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<StartResult> start(@PathVariable Long workspaceId, @RequestParam(name = "entryIds", required = false) List<Long> entryIds) {
        if (entryIds == null || entryIds.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one RUN entry.");
        List<RunConfigurationService.LaunchSpec> specs = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(entryIds)) specs.add(configurations.launchSpec(workspaceId, id));
        configurations.markSelected(workspaceId, entryIds);
        List<Long> started = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (RunConfigurationService.LaunchSpec spec : specs) {
            try {
                if (processes.start(spec)) started.add(spec.entryId());
            } catch (RunProcessRegistry.StartException failed) {
                errors.add(failed.getMessage());
            }
        }
        return ResponseEntity.ok(new StartResult(started, errors));
    }

    @PostMapping("/{entryId}/stop")
    @ResponseBody
    public ResponseEntity<Void> stop(@PathVariable Long workspaceId, @PathVariable Long entryId) {
        configurations.entry(workspaceId, entryId);
        processes.stop(workspaceId, entryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{entryId}/restart")
    @ResponseBody
    public ResponseEntity<String> restart(@PathVariable Long workspaceId, @PathVariable Long entryId) {
        try {
            processes.restart(configurations.launchSpec(workspaceId, entryId));
            return ResponseEntity.noContent().build();
        } catch (RunProcessRegistry.StartException failed) {
            return ResponseEntity.badRequest().body(failed.getMessage());
        }
    }

    /** State and buffered output of every entry with a process or retained log. */
    @GetMapping(value = "/state", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<RunProcessRegistry.Snapshot> state(@PathVariable Long workspaceId) {
        workspace(workspaceId);
        return processes.snapshot(workspaceId);
    }

    /** Dismisses the retained log of a stopped or crashed entry. */
    @DeleteMapping("/{entryId}/log")
    @ResponseBody
    public ResponseEntity<String> dismiss(@PathVariable Long workspaceId, @PathVariable Long entryId) {
        workspace(workspaceId);
        try {
            processes.dismiss(workspaceId, entryId);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException running) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(running.getMessage());
        }
    }

    // ---- Helpers -------------------------------------------------------------------------

    /** Form binding; environment rows arrive as parallel envName/envValue parameters. */
    public record EntryParams(String name, Long projectId, String workingDirectory, String command,
                              List<String> envName, List<String> envValue, Boolean useWorkspaceJava) {
        RunConfigurationService.Form form() {
            return new RunConfigurationService.Form(name, projectId, workingDirectory, command,
                envName == null ? List.of() : envName, envValue == null ? List.of() : envValue, Boolean.TRUE.equals(useWorkspaceJava));
        }
    }

    private static RunConfigurationService.Form emptyForm() {
        return new RunConfigurationService.Form(null, null, null, null, List.of(), List.of(), true);
    }

    private String config(Long workspaceId, Model model, Long editingId, RunConfigurationService.Form form, Map<String, String> errors) {
        Workspace workspace = workspace(workspaceId);
        model.addAttribute("workspace", workspace);
        model.addAttribute("entries", configurations.views(workspaceId));
        model.addAttribute("projects", configurations.projectOptions(workspaceId));
        model.addAttribute("running", processes.runningEntries(workspaceId));
        model.addAttribute("editingId", editingId);
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        return FRAGMENTS + " :: run-config-content";
    }

    private Workspace workspace(Long workspaceId) {
        return workspaces.getWorkspace(workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace does not exist."));
    }
}
