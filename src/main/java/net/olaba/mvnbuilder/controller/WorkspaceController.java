package net.olaba.mvnbuilder.controller;

import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.model.M2ProjectInfo;
import net.olaba.mvnbuilder.repository.MavenProjectRepository;

import net.olaba.mvnbuilder.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.util.List;

/**
 * Controller for managing workspace operations and project views.
 */
@Controller
@RequestMapping("/")
@RequiredArgsConstructor
@Slf4j
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final BuildService buildService;
    private final TopologicalSortService topologicalSortService;
    private final MavenProjectRepository mavenProjectRepository;
    private final GitService gitService;
    private final MavenRepositoryService mavenRepositoryService;
    private final FileSystemService fileSystemService;
    private final MavenService mavenService;

    @ModelAttribute("availableGroupIds")
    public List<String> getAvailableGroupIds() {
        return mavenRepositoryService.getM2TopLevelFolders();
    }

    /**
     * Renders the home page with all workspaces and available group IDs.
     * 
     * @param model The UI model.
     * @return The index view name.
     */
    @GetMapping
    public String index(final Model model) {
        model.addAttribute("workspaces", workspaceService.getAllWorkspaces());
        return "index";
    }

    /**
     * Creates a new workspace.
     * 
     * @param name     The workspace name.
     * @param basePath The base path for projects.
     * @param skipScan Whether to skip the initial scan.
     * @return Redirect to home.
     */
    @PostMapping("/workspaces")
    public String createWorkspace(final @RequestParam String name,
            final @RequestParam(required = false) String basePath,
            final @RequestParam(required = false, defaultValue = "false") boolean skipScan) {
        workspaceService.createWorkspace(name, basePath, skipScan);
        return "redirect:/";
    }

    /**
     * Updates an existing workspace.
     * 
     * @param id       The workspace ID.
     * @param name     The new name.
     * @param basePath The new base path.
     * @return Redirect to workspace detail.
     */
    @PostMapping("/workspaces/{id}/edit")
    public String updateWorkspace(final @PathVariable Long id, final @RequestParam String name,
            final @RequestParam(required = false) String basePath) {
        workspaceService.updateWorkspace(id, name, basePath);
        return "redirect:/workspaces/" + id;
    }

    /**
     * Deletes a workspace.
     * 
     * @param id The workspace ID.
     * @return Redirect to home.
     */
    @PostMapping("/workspaces/{id}/delete")
    public String deleteWorkspace(final @PathVariable Long id) {
        workspaceService.deleteWorkspace(id);
        return "redirect:/";
    }

    /**
     * Duplicates a workspace.
     * 
     * @param id The workspace ID.
     * @return Redirect to the new cloned workspace.
     */
    @PostMapping("/workspaces/{id}/duplicate")
    public String duplicateWorkspace(final @PathVariable Long id) {
        final Workspace copy = workspaceService.duplicateWorkspace(id);
        return "redirect:/workspaces/" + copy.getId();
    }

    /**
     * Displays the workspace details.
     * 
     * @param id    The workspace ID.
     * @param model The UI model.
     * @return The workspace detail view.
     */
    @GetMapping("/workspaces/{id}")
    public String viewWorkspace(final @PathVariable Long id, final Model model) {
        workspaceService.refreshGitStatus(id);
        final Workspace workspace = workspaceService.getWorkspace(id).orElseThrow();
        model.addAttribute("workspace", workspace);
        model.addAttribute("projects", workspaceService.getProjectsForWorkspace(id, true));
        return "workspace-detail";
    }

    /**
     * Deletes a project from a workspace.
     * 
     * @param id The project ID.
     * @return A success response with redirect header.
     */
    @PostMapping("/projects/{id}/delete")
    @ResponseBody
    public ResponseEntity<Void> deleteProject(final @PathVariable Long id) {
        final MavenProject project = mavenProjectRepository.findById(id).orElseThrow();
        final Long workspaceId = project.getWorkspace().getId();
        log.info("Deleting project '{}' and its children from workspace {}", project.getArtifactId(), workspaceId);
        workspaceService.deleteProject(id);
        return ResponseEntity.ok()
                .header("HX-Redirect", "/workspaces/" + workspaceId)
                .build();
    }

    /**
     * Refreshes the workspace state.
     * 
     * @param id The workspace ID.
     * @return Redirect to workspace detail.
     */
    @PostMapping("/workspaces/{id}/refresh")
    public String refreshWorkspace(final @PathVariable Long id) {
        workspaceService.refreshWorkspace(id);
        return "redirect:/workspaces/" + id;
    }

    /**
     * Calculates the topological build order for a workspace.
     * 
     * @param id The workspace ID.
     * @return Redirect to workspace detail.
     */
    @PostMapping("/workspaces/{id}/calculate-order")
    public String calculateOrder(final @PathVariable Long id, final RedirectAttributes redirectAttributes) {
        try {
            topologicalSortService.calculateOrder(id);
        } catch (Exception e) {
            log.error("Error calculating build order for workspace {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/workspaces/" + id;
    }

    /**
     * Triggers a sequential build of the entire workspace.
     * 
     * @param id The workspace ID.
     */
    @PostMapping("/workspaces/{id}/build-all")
    @ResponseBody
    public void buildAll(final @PathVariable Long id) {
        buildService.buildWorkspaceSequentially(id);
    }

    /**
     * Manually updates the project build order.
     * 
     * @param id         The workspace ID.
     * @param projectIds The ordered list of project IDs.
     * @return A success response.
     */
    @PostMapping("/workspaces/{id}/update-order")
    @ResponseBody
    public ResponseEntity<String> updateOrder(final @PathVariable Long id, final @RequestBody List<Long> projectIds) {
        workspaceService.updateProjectOrder(id, projectIds);
        return ResponseEntity.ok("Success");
    }

    /**
     * Executes bulk actions on selected projects.
     * 
     * @param id         The workspace ID.
     * @param projectIds The list of project IDs.
     * @param action     The action to perform (build, fetch, pull).
     */
    @PostMapping("/workspaces/{id}/bulk-action")
    @ResponseBody
    public void bulkAction(final @PathVariable Long id, final @RequestParam List<Long> projectIds,
            final @RequestParam String action) {
        final List<MavenProject> unsortedProjects = mavenProjectRepository.findAllById(projectIds);
        final java.util.Map<Long, MavenProject> projectMap = unsortedProjects.stream()
                .collect(java.util.stream.Collectors.toMap(MavenProject::getId, java.util.function.Function.identity()));
        final List<MavenProject> projects = projectIds.stream()
                .map(projectMap::get)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());

        switch (action) {
            case "build":
                buildService.buildProjectsSequentially(projects);
                break;
            case "fetch":
                buildService.bulkGitFetch(projects);
                break;
            case "pull":
                buildService.bulkGitPull(projects);
                break;
            case "checkout-discard":
                buildService.bulkGitDiscard(projects);
                break;
            case "restore-staged":
                buildService.bulkGitUnstage(projects);
                break;
            default:
                log.warn("Unknown bulk action: {}", action);
                break;
        }
    }

    /**
     * Exports the projects in execution order as a text file.
     * 
     * @param id The workspace ID.
     * @return A plain text response containing project artifact IDs.
     */
    @GetMapping("/workspaces/{id}/export-order")
    public ResponseEntity<String> exportOrder(final @PathVariable Long id) {
        final List<MavenProject> projects = workspaceService.getProjectsForWorkspace(id, true);
        final StringBuilder sb = new StringBuilder();
        for (final MavenProject project : projects) {
            sb.append(project.getArtifactId()).append("\n");
        }
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"build-order-" + id + ".txt\"")
                .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                .body(sb.toString());
    }

    /**
     * Executes bulk branch checkout/creation on multiple projects.
     * 
     * @param id         The workspace ID.
     * @param projectIds The list of project IDs.
     * @param branch     The target branch name.
     * @param createNew  Whether to create the branch if it doesn't exist (-b).
     * @return A success message.
     */
    @PostMapping("/workspaces/{id}/bulk-checkout")
    @ResponseBody
    public ResponseEntity<String> bulkCheckout(
            final @PathVariable Long id,
            final @RequestParam List<Long> projectIds,
            final @RequestParam String branch,
            final @RequestParam(required = false, defaultValue = "false") boolean createNew) {
        log.info("Bulk checkout to branch '{}' (create: {}) for workspace {}, project IDs: {}", branch, createNew, id, projectIds);
        
        final List<MavenProject> unsortedProjects = mavenProjectRepository.findAllById(projectIds);
        final java.util.Map<Long, MavenProject> projectMap = unsortedProjects.stream()
                .collect(java.util.stream.Collectors.toMap(MavenProject::getId, java.util.function.Function.identity()));
        final List<MavenProject> projects = projectIds.stream()
                .map(projectMap::get)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());

        final List<String> succeeded = new java.util.ArrayList<>();
        final List<String> failed = new java.util.ArrayList<>();

        for (final MavenProject project : projects) {
            if (!project.isEnabled()) continue;
            final File projectDir = new File(new File(project.getWorkspace().getBasePath()), project.getRelativePath());
            try {
                if (createNew) {
                    gitService.createBranch(projectDir, branch);
                } else {
                    gitService.checkoutBranch(projectDir, branch);
                }
                project.setGitBranch(branch);
                mavenProjectRepository.save(project);
                succeeded.add(project.getArtifactId());
            } catch (final Exception e) {
                log.error("Failed bulk checkout for project '{}': {}", project.getArtifactId(), e.getMessage());
                failed.add(project.getArtifactId() + " (" + e.getMessage() + ")");
            }
        }

        if (!failed.isEmpty()) {
            return ResponseEntity.badRequest().body("Failed for: " + String.join(", ", failed));
        }
        return ResponseEntity.ok("Success");
    }

    /**
     * Builds a single project.
     * 
     * @param id The project ID.
     */
    @PostMapping("/projects/{id}/build")
    @ResponseBody
    public void buildProject(final @PathVariable Long id) {
        final MavenProject project = mavenProjectRepository.findById(id).orElseThrow();
        buildService.buildProject(project);
    }

    /**
     * Toggles the enabled status of a project.
     * 
     * @param id The project ID.
     * @return The new enabled status.
     */
    @PostMapping("/projects/{id}/toggle-enabled")
    @ResponseBody
    public ResponseEntity<Boolean> toggleEnabled(final @PathVariable Long id) {
        final MavenProject project = mavenProjectRepository.findById(id).orElseThrow();
        project.setEnabled(!project.isEnabled());
        mavenProjectRepository.save(project);
        return ResponseEntity.ok(project.isEnabled());
    }

    /**
     * Fetches updates for a project via Git.
     * 
     * @param id The project ID.
     */
    @PostMapping("/projects/{id}/fetch")
    @ResponseBody
    public void fetchProject(final @PathVariable Long id) {
        final MavenProject project = mavenProjectRepository.findById(id).orElseThrow();
        buildService.gitFetch(project);
    }

    /**
     * Pulls updates for a project via Git.
     * 
     * @param id The project ID.
     */
    @PostMapping("/projects/{id}/pull")
    @ResponseBody
    public void pullProject(final @PathVariable Long id) {
        final MavenProject project = mavenProjectRepository.findById(id).orElseThrow();
        buildService.gitPull(project);
    }

    /**
     * Lists available Git branches for a project.
     * 
     * @param id    The project ID.
     * @param model The UI model.
     * @return The branch selector fragment.
     */
    @GetMapping("/projects/{id}/branches")
    public String getBranches(final @PathVariable Long id, final Model model) {
        final MavenProject project = mavenProjectRepository.findById(id).orElseThrow();
        final Workspace workspace = project.getWorkspace();
        final File projectDir = new File(new File(workspace.getBasePath()), project.getRelativePath());
        final List<String> branches = gitService.listBranches(projectDir);

        model.addAttribute("project", project);
        model.addAttribute("branches", branches);
        model.addAttribute("currentBranch", project.getGitBranch());
        return "fragments/branch-selector :: branch-list";
    }

    /**
     * Switches a project to a specific Git branch.
     * 
     * @param id     The project ID.
     * @param branch The branch name.
     * @return A success or error response.
     */
    @PostMapping("/projects/{id}/checkout")
    @ResponseBody
    public ResponseEntity<String> checkoutBranch(final @PathVariable Long id, final @RequestParam String branch) {
        log.info("Request to checkout branch '{}' for project ID {}", branch, id);
        final MavenProject project = mavenProjectRepository.findById(id).orElseThrow();
        final Workspace workspace = project.getWorkspace();
        final File projectDir = new File(new File(workspace.getBasePath()), project.getRelativePath());

        try {
            gitService.checkoutBranch(projectDir, branch);
            project.setGitBranch(branch);
            mavenProjectRepository.save(project);
            return ResponseEntity.ok("Success");
        } catch (final Exception e) {
            log.error("Failed to checkout branch: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    /**
     * Creates a new Git branch for a project.
     * 
     * @param id     The project ID.
     * @param branch The branch name.
     * @return A success or error response.
     */
    @PostMapping("/projects/{id}/create-branch")
    @ResponseBody
    public ResponseEntity<String> createBranch(final @PathVariable Long id, final @RequestParam String branch) {
        log.info("Request to create branch '{}' for project ID {}", branch, id);
        final MavenProject project = mavenProjectRepository.findById(id).orElseThrow();
        final Workspace workspace = project.getWorkspace();
        final File projectDir = new File(new File(workspace.getBasePath()), project.getRelativePath());

        try {
            gitService.createBranch(projectDir, branch);
            project.setGitBranch(branch);
            mavenProjectRepository.save(project);
            return ResponseEntity.ok("Success");
        } catch (final Exception e) {
            log.error("Failed to create branch: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    /**
     * Retrieves Maven repository information (M2) for a project.
     * 
     * @param id    The project ID.
     * @param model The UI model.
     * @return The M2 info fragment.
     */
    @GetMapping("/projects/{id}/m2")
    public String getM2Info(final @PathVariable Long id, final Model model) {
        final MavenProject project = mavenProjectRepository.findById(id).orElseThrow();
        final M2ProjectInfo m2Info = mavenRepositoryService.getProjectInfo(project.getId(), project.getGroupId(),
                project.getArtifactId());

        // Find submodules (children) using robust logic
        final List<MavenProject> allProjects = workspaceService.getProjectsForWorkspace(project.getWorkspace().getId(),
                false);
        final List<M2ProjectInfo> childM2Infos = allProjects.stream()
                .filter(p -> workspaceService.isChildOf(p, project))
                .map(p -> mavenRepositoryService.getProjectInfo(p.getId(), p.getGroupId(), p.getArtifactId()))

                .collect(java.util.stream.Collectors.toList());

        model.addAttribute("project", project);
        model.addAttribute("m2Info", m2Info);
        model.addAttribute("childM2Infos", childM2Infos);
        return "fragments/m2-info :: m2-list";
    }

    /**
     * Deletes a specific version of a project from the M2 repository.
     * 
     * @param id      The project ID.
     * @param version The version to delete.
     * @param model   The UI model.
     * @return The updated M2 info fragment.
     */
    @PostMapping("/projects/{id}/m2/delete-version")
    public String deleteM2Version(final @PathVariable Long id, final @RequestParam String version, final Model model) {
        final MavenProject project = mavenProjectRepository.findById(id).orElseThrow();
        log.info("Deleting M2 version {} for project {}", version, project.getArtifactId());

        // Delete for project
        mavenRepositoryService.deleteVersion(project.getGroupId(), project.getArtifactId(), version);

        // Delete for all submodules
        final List<MavenProject> allProjects = workspaceService.getProjectsForWorkspace(project.getWorkspace().getId(),
                false);
        allProjects.stream()
                .filter(p -> workspaceService.isChildOf(p, project))
                .forEach(p -> {
                    log.info("Deleting M2 version {} for submodule {}", version, p.getArtifactId());
                    mavenRepositoryService.deleteVersion(p.getGroupId(), p.getArtifactId(), version);
                });

        return getM2Info(id, model);
    }

    /**
     * Deletes all versions of a project from the M2 repository.
     * 
     * @param id    The project ID.
     * @param model The UI model.
     * @return The updated M2 info fragment.
     */
    @PostMapping("/projects/{id}/m2/delete-all")
    public String deleteM2Project(final @PathVariable Long id, final Model model) {
        final MavenProject project = mavenProjectRepository.findById(id).orElseThrow();
        log.info("Deleting ALL M2 versions for project {}", project.getArtifactId());

        // Delete for project
        mavenRepositoryService.deleteProject(project.getGroupId(), project.getArtifactId());

        // Delete for all submodules
        final List<MavenProject> allProjects = workspaceService.getProjectsForWorkspace(project.getWorkspace().getId(),
                false);
        allProjects.stream()
                .filter(p -> workspaceService.isChildOf(p, project))
                .forEach(p -> {
                    log.info("Deleting ALL M2 versions for submodule {}", p.getArtifactId());
                    mavenRepositoryService.deleteProject(p.getGroupId(), p.getArtifactId());
                });

        return getM2Info(id, model);
    }

    /**
     * Lists direct child projects (submodules).
     * 
     * @param id    The project ID.
     * @param model The UI model.
     * @return The project children fragment.
     */
    @GetMapping("/projects/{id}/children")
    public String getProjectChildren(final @PathVariable Long id, final Model model) {
        final MavenProject project = mavenProjectRepository.findById(id).orElseThrow();
        final List<MavenProject> allProjects = workspaceService.getProjectsForWorkspace(project.getWorkspace().getId(),
                false);

        final List<MavenProject> children = allProjects.stream()
                .filter(p -> workspaceService.isDirectChildOf(p, project, allProjects))
                .sorted(java.util.Comparator.comparing(MavenProject::getRelativePath))
                .collect(java.util.stream.Collectors.toList());

        // Find parent of current project to allow "Go back"
        final MavenProject grandParent = allProjects.stream()
                .filter(p -> workspaceService.isDirectChildOf(project, p, allProjects))
                .findFirst().orElse(null);

        model.addAttribute("parentProject", project);
        model.addAttribute("grandParent", grandParent);
        model.addAttribute("children", children);
        return "fragments/project-children :: children-list";
    }

    /**
     * Shows the file explorer for a workspace path.
     * 
     * @param id    The workspace ID.
     * @param path  The directory path.
     * @param model The UI model.
     * @return The explorer fragment.
     */
    @GetMapping("/workspaces/{id}/explorer")
    public String showExplorer(final @PathVariable Long id, final @RequestParam(required = false) String path,
            final Model model) {
        final Workspace workspace = workspaceService.getWorkspace(id).orElseThrow();
        final String currentPath = (path == null || path.isEmpty()) ? workspace.getBasePath() : path;

        final List<FileSystemService.FileItem> items = fileSystemService.listDirectory(currentPath);
        final File currentDir = new File(currentPath);

        model.addAttribute("workspace", workspace);
        model.addAttribute("items", items);
        model.addAttribute("currentPath", currentPath);
        model.addAttribute("parentPath", currentDir.getParent());

        return "fragments/explorer :: explorer-content";
    }

    /**
     * Adds projects to a workspace from selected file system paths.
     * 
     * @param id    The workspace ID.
     * @param paths The list of directory paths.
     * @return A success response.
     */
    @PostMapping("/workspaces/{id}/explorer/add")
    @ResponseBody
    public ResponseEntity<String> addProjects(final @PathVariable Long id, final @RequestParam List<String> paths) {
        for (final String path : paths) {
            workspaceService.addProjectByPath(id, path);
        }
        return ResponseEntity.ok("Projects added");
    }

    public static class KeyPropertyInfo {
        private final String key;
        private final String label;
        private final String category;
        private String value;
        private String source; // "project", "parent", or null

        public KeyPropertyInfo(String key, String label, String category) {
            this.key = key;
            this.label = label;
            this.category = category;
        }

        public String getKey() { return key; }
        public String getLabel() { return label; }
        public String getCategory() { return category; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }

    private static final List<KeyPropertyInfo> KEY_PROPERTY_BLUEPRINTS = List.of(
        new KeyPropertyInfo("revision", "Revision", "Build & Compiler"),
        new KeyPropertyInfo("project.build.sourceEncoding", "Encoding", "Build & Compiler"),
        new KeyPropertyInfo("maven.compiler.source", "Compiler Source", "Build & Compiler"),
        new KeyPropertyInfo("maven.compiler.target", "Compiler Target", "Build & Compiler"),
        new KeyPropertyInfo("lombok.version", "Lombok", "Logging & Utils"),
        new KeyPropertyInfo("slf4j.version", "SLF4J", "Logging & Utils"),
        new KeyPropertyInfo("logback.version", "Logback", "Logging & Utils"),
        new KeyPropertyInfo("guava.version", "Guava", "Logging & Utils"),
        new KeyPropertyInfo("gson.version", "Gson", "Logging & Utils"),
        new KeyPropertyInfo("jackson.version", "Jackson Version", "Jackson JSON"),
        new KeyPropertyInfo("jackson.annotations.version", "Jackson Annotations", "Jackson JSON"),
        new KeyPropertyInfo("weblogic.thint3client.version", "WebLogic Thin T3 Client", "WebLogic Clients"),
        new KeyPropertyInfo("weblogic.webservicesclient.version", "WebLogic Web Services Client", "WebLogic Clients")
    );

    /**
     * Retrieves all properties for a project, including resolved key properties,
     * project-level properties, and parent-level properties.
     * 
     * @param id    The project ID.
     * @param model The UI model.
     * @return The project properties fragment.
     */
    @GetMapping("/projects/{id}/properties")
    public String getProjectProperties(final @PathVariable Long id, final Model model) {
        final MavenProject project = mavenProjectRepository.findById(id).orElseThrow();
        final java.util.Properties projectProps = mavenService.getProjectProperties(project);
        final java.util.Properties parentProps = project.getParentKey() != null 
                ? mavenService.getParentPomProperties(project) 
                : new java.util.Properties();

        final List<KeyPropertyInfo> resolvedKeyProperties = new java.util.ArrayList<>();
        for (final KeyPropertyInfo blueprint : KEY_PROPERTY_BLUEPRINTS) {
            final KeyPropertyInfo resolved = new KeyPropertyInfo(blueprint.getKey(), blueprint.getLabel(), blueprint.getCategory());
            if (projectProps.containsKey(blueprint.getKey())) {
                resolved.setValue(projectProps.getProperty(blueprint.getKey()));
                resolved.setSource("project");
            } else if (parentProps.containsKey(blueprint.getKey())) {
                resolved.setValue(parentProps.getProperty(blueprint.getKey()));
                resolved.setSource("parent");
            } else {
                resolved.setValue("Not defined");
                resolved.setSource(null);
            }
            resolvedKeyProperties.add(resolved);
        }

        final java.util.Map<String, List<KeyPropertyInfo>> groupedKeyProperties = resolvedKeyProperties.stream()
                .collect(java.util.stream.Collectors.groupingBy(KeyPropertyInfo::getCategory, 
                        java.util.LinkedHashMap::new, 
                        java.util.stream.Collectors.toList()));

        final List<java.util.Map.Entry<Object, Object>> sortedProjectProps = projectProps.entrySet().stream()
                .sorted(java.util.Comparator.comparing(e -> e.getKey().toString().toLowerCase()))
                .collect(java.util.stream.Collectors.toList());

        final List<java.util.Map.Entry<Object, Object>> sortedParentProps = parentProps.entrySet().stream()
                .sorted(java.util.Comparator.comparing(e -> e.getKey().toString().toLowerCase()))
                .collect(java.util.stream.Collectors.toList());

        model.addAttribute("project", project);
        model.addAttribute("groupedKeyProperties", groupedKeyProperties);
        model.addAttribute("projectProperties", sortedProjectProps);
        model.addAttribute("parentProperties", sortedParentProps);
        model.addAttribute("parentKey", project.getParentKey());
        
        return "fragments/project-properties :: project-properties-list";
    }
}
