package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.repository.MavenProjectRepository;
import net.olaba.mvnbuilder.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing workspaces and their associated Maven projects.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final MavenProjectRepository mavenProjectRepository;
    private final MavenService mavenService;
    private final GitService gitService;

    /**
     * Retrieves all configured workspaces.
     * 
     * @return A list of all workspaces.
     */
    public List<Workspace> getAllWorkspaces() {
        return workspaceRepository.findAll();
    }

    /**
     * Retrieves a workspace by its ID.
     * 
     * @param id The workspace ID.
     * @return An Optional containing the workspace if found.
     */
    public Optional<Workspace> getWorkspace(final Long id) {
        return workspaceRepository.findById(id);
    }

    /**
     * Creates a new workspace and optionally scans for projects.
     * 
     * @param name     The workspace name.
     * @param basePath The file system path for the workspace.
     * @param skipScan Whether to skip the automatic project discovery.
     * @return The created workspace.
     */
    @Transactional
    public Workspace createWorkspace(final String name, final String basePath, final boolean skipScan) {
        Workspace workspace = Workspace.builder()
                .withName(name)
                .withBasePath(basePath)
                .build();
        workspace = workspaceRepository.save(workspace);
        if (!skipScan) {
            scanAndImportProjects(workspace);
        }
        return workspace;
    }

    /**
     * Updates an existing workspace. Re-scans projects if the base path changes.
     * 
     * @param id       The workspace ID.
     * @param name     The new name.
     * @param basePath The new base path.
     * @return The updated workspace.
     */
    @Transactional
    public Workspace updateWorkspace(final Long id, final String name, final String basePath) {
        final Workspace workspace = workspaceRepository.findById(id).orElseThrow();
        final boolean pathChanged = (basePath != null && !basePath.equals(workspace.getBasePath())) ||
                (basePath == null && workspace.getBasePath() != null);

        workspace.setName(name);
        workspace.setBasePath(basePath);
        final Workspace savedWorkspace = workspaceRepository.save(workspace);

        if (pathChanged && basePath != null) {
            // Re-scan projects if path changed
            mavenProjectRepository.deleteByWorkspaceId(id);
            scanAndImportProjects(savedWorkspace);
        }

        return savedWorkspace;
    }

    /**
     * Deletes a workspace by its ID.
     * 
     * @param id The workspace ID.
     */
    @Transactional
    public void deleteWorkspace(final Long id) {
        workspaceRepository.deleteById(id);
    }

    /**
     * Saves (merges) a workspace entity.
     * 
     * @param workspace The workspace to save.
     * @return The persisted workspace.
     */
    @Transactional
    public Workspace saveWorkspace(final Workspace workspace) {
        return workspaceRepository.save(workspace);
    }

    /**
     * Duplicates an existing workspace and all its projects.
     * 
     * @param id The ID of the workspace to duplicate.
     * @return The new duplicated workspace.
     */
    @Transactional
    public Workspace duplicateWorkspace(final Long id) {
        final Workspace original = workspaceRepository.findById(id).orElseThrow();

        Workspace copy = Workspace.builder()
                .withName(original.getName() + " (Copy)")
                .withBasePath(original.getBasePath())
                .build();
        copy.getExcludedPaths().addAll(original.getExcludedPaths());
        copy = workspaceRepository.save(copy);

        final List<MavenProject> projects = mavenProjectRepository.findByWorkspaceIdOrderByExecutionOrderAsc(id);
        for (final MavenProject originalProject : projects) {
            MavenProject projectCopy = MavenProject.builder()
                    .withArtifactId(originalProject.getArtifactId())
                    .withGroupId(originalProject.getGroupId())
                    .withVersion(originalProject.getVersion())
                    .withRelativePath(originalProject.getRelativePath())
                    .withAbsolutePath(originalProject.getAbsolutePath())
                    .withGitBranch(originalProject.getGitBranch())
                    .withExecutionOrder(originalProject.getExecutionOrder())
                    .withParentKey(originalProject.getParentKey())
                    .withEnabled(originalProject.isEnabled())
                    .build();
            projectCopy.getModules().addAll(originalProject.getModules());
            projectCopy.getInternalDependencies().addAll(originalProject.getInternalDependencies());
            projectCopy.setWorkspace(copy);
            mavenProjectRepository.save(projectCopy);
        }

        return copy;
    }

    /**
     * Deletes a project and its submodules, adding its path to exclusions.
     * 
     * @param projectId The project ID to delete.
     */
    @Transactional
    public void deleteProject(final Long projectId) {
        final MavenProject project = mavenProjectRepository.findById(projectId).orElseThrow();
        final Workspace workspace = project.getWorkspace();
        final Long workspaceId = workspace.getId();
        final List<MavenProject> allProjects = mavenProjectRepository
                .findByWorkspaceIdOrderByExecutionOrderAsc(workspaceId);

        // Add the project's relativePath to the workspace exclusion list
        if (!workspace.getExcludedPaths().contains(project.getRelativePath())) {
            workspace.getExcludedPaths().add(project.getRelativePath());
            workspaceRepository.save(workspace);
        }

        // Delete all recursive children first
        final List<MavenProject> toDelete = new ArrayList<>();
        toDelete.add(project);
        for (final MavenProject p : allProjects) {
            if (isChildOf(p, project)) {
                toDelete.add(p);
            }
        }

        mavenProjectRepository.deleteAll(toDelete);
    }

    /**
     * Scans the workspace base path for pom.xml files and imports them.
     * 
     * @param workspace The workspace to scan.
     */
    @Transactional
    public void scanAndImportProjects(final Workspace workspace) {
        if (workspace.getBasePath() == null) {
            return;
        }
        final File baseDir = new File(workspace.getBasePath());
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            return; // Don't throw exception here, just don't import
        }

        final List<File> pomFiles = new ArrayList<>();
        findPomFiles(baseDir, pomFiles);

        // Get excluded paths
        final Set<String> excludedPaths = new HashSet<>(workspace.getExcludedPaths());
        final java.util.Map<File, String> gitRootBranchCache = new java.util.HashMap<>();

        for (final File pomFile : pomFiles) {
            // Calculate relative path of this pom
            String relativePath = baseDir.toPath().relativize(pomFile.getParentFile().toPath()).toString();
            relativePath = relativePath.replace('\\', '/'); // Normalize for Windows
            if (relativePath.isEmpty()) {
                relativePath = ".";
            }

            // Skip if excluded or is a child of an excluded path
            boolean excluded = false;
            for (String ep : excludedPaths) {
                ep = ep.replace('\\', '/'); // Normalize exclusion path
                if (relativePath.equals(ep) || relativePath.startsWith(ep + "/")) {
                    excluded = true;
                    break;
                }
            }

            if (!excluded) {
                importProject(workspace, pomFile, gitRootBranchCache);
            }
        }

        // Scan manually added parent projects that reside outside the base path
        final List<MavenProject> currentProjects = mavenProjectRepository
                .findByWorkspaceIdOrderByExecutionOrderAsc(workspace.getId());
        
        final String baseAbsNormalized;
        try {
            baseAbsNormalized = baseDir.getAbsoluteFile().toPath().normalize().toString();
        } catch (final Exception e) {
            return;
        }

        final List<MavenProject> externalParents = currentProjects.stream()
                .filter(p -> isParentProject(p, currentProjects))
                .filter(p -> {
                    if (p.getAbsolutePath() == null) {
                        return false;
                    }
                    try {
                        final String pAbsNormalized = new File(p.getAbsolutePath()).getAbsoluteFile().toPath().normalize().toString();
                        return !pAbsNormalized.startsWith(baseAbsNormalized);
                    } catch (final Exception e) {
                        return true;
                    }
                })
                .collect(Collectors.toList());

        for (final MavenProject extParent : externalParents) {
            final File extDir = new File(extParent.getAbsolutePath());
            if (extDir.exists() && extDir.isDirectory()) {
                final List<File> extPomFiles = new ArrayList<>();
                findPomFiles(extDir, extPomFiles);
                for (final File pomFile : extPomFiles) {
                    importProject(workspace, pomFile, gitRootBranchCache);
                }
            }
        }
    }

    /**
     * Manually adds a project by its file system path.
     * 
     * @param workspaceId The workspace ID.
     * @param projectPath The directory path containing a pom.xml.
     */
    @Transactional
    public void addProjectByPath(final Long workspaceId, final String projectPath) {
        final Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow();
        final File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            return;
        }
        final List<File> pomFiles = new ArrayList<>();
        findPomFiles(projectDir, pomFiles);
        final java.util.Map<File, String> gitRootBranchCache = new java.util.HashMap<>();
        for (final File pomFile : pomFiles) {
            importProject(workspace, pomFile, gitRootBranchCache);
        }
    }

    /**
     * Imports or updates a single Maven project into a workspace.
     * 
     * @param workspace The workspace.
     * @param pomFile   The pom.xml file.
     */
    private void importProject(final Workspace workspace, final File pomFile) {
        importProject(workspace, pomFile, new java.util.HashMap<>());
    }

    /**
     * Imports or updates a single Maven project into a workspace, using a cache for Git branches.
     * 
     * @param workspace   The workspace.
     * @param pomFile     The pom.xml file.
     * @param branchCache The Git root branch cache.
     */
    private void importProject(final Workspace workspace, final File pomFile, final java.util.Map<File, String> branchCache) {
        final MavenProject projectData = mavenService.parsePom(pomFile, workspace.getBasePath());

        final Optional<MavenProject> existingOpt = mavenProjectRepository
                .findByWorkspaceIdAndRelativePath(workspace.getId(), projectData.getRelativePath());

        final MavenProject projectToSave;
        if (existingOpt.isPresent()) {
            projectToSave = existingOpt.get();
            projectToSave.setArtifactId(projectData.getArtifactId());
            projectToSave.setGroupId(projectData.getGroupId());
            projectToSave.setVersion(projectData.getVersion());
            projectToSave.setModules(projectData.getModules());
            projectToSave.setInternalDependencies(projectData.getInternalDependencies());
            projectToSave.setAbsolutePath(projectData.getAbsolutePath());
            projectToSave.setParentKey(projectData.getParentKey());
        } else {
            projectToSave = projectData;
            projectToSave.setWorkspace(workspace);
        }

        final File projectDir = pomFile.getParentFile();
        final File gitRoot = findGitRoot(projectDir);
        final File cacheKey = gitRoot != null ? gitRoot : projectDir;

        final String branch = branchCache.computeIfAbsent(cacheKey, key -> gitService.getCurrentBranch(key));
        projectToSave.setGitBranch(branch);
        mavenProjectRepository.save(projectToSave);
    }

    /**
     * Recursively finds all pom.xml files starting from a directory.
     * 
     * @param dir      The directory to search in.
     * @param pomFiles The list to accumulate found files.
     */
    private void findPomFiles(final File dir, final List<File> pomFiles) {
        final File pom = new File(dir, "pom.xml");
        if (pom.exists()) {
            pomFiles.add(pom);
        }

        final File[] files = dir.listFiles();
        if (files != null) {
            for (final File file : files) {
                final String name = file.getName();
                if (file.isDirectory() && !name.startsWith(".") 
                        && !name.equals("target") 
                        && !name.equals("node_modules")
                        && !name.equals("src")
                        && !name.equals("build")
                        && !name.equals("out")
                        && !name.equals("dist")
                        && !name.equals("bin")
                        && !name.equals("gradle")) {
                    findPomFiles(file, pomFiles);
                }
            }
        }
    }

    /**
     * Retrieves all projects for a workspace, optionally filtered to top-level
     * projects only.
     * 
     * @param workspaceId The workspace ID.
     * @param parentsOnly Whether to return only projects with no parents in the
     *                    workspace.
     * @return A list of Maven projects.
     */
    public List<MavenProject> getProjectsForWorkspace(final Long workspaceId, final boolean parentsOnly) {
        final List<MavenProject> projects = mavenProjectRepository
                .findByWorkspaceIdOrderByExecutionOrderAsc(workspaceId);
        if (parentsOnly) {
            return projects.stream()
                    .filter(p -> isParentProject(p, projects))
                    .collect(Collectors.toList());
        }
        return projects;
    }

    /**
     * Checks if a project is a top-level project in the given list.
     * 
     * @param project     The project to check.
     * @param allProjects The list of all projects in the workspace.
     * @return True if the project has no ancestors in the list.
     */
    private boolean isParentProject(final MavenProject project, final List<MavenProject> allProjects) {
        // A project is a parent if no other project in the workspace is its physical
        // ancestor
        for (final MavenProject other : allProjects) {
            if (isChildOf(project, other)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Refreshes the Git branch status for all projects in a workspace.
     * 
     * @param workspaceId The workspace ID.
     */
    @Transactional
    public void refreshGitStatus(final Long workspaceId) {
        final Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow();
        if (workspace.getBasePath() == null) {
            return;
        }

        final List<MavenProject> projects = getProjectsForWorkspace(workspaceId, true);
        final java.util.Map<File, String> gitRootBranchCache = new java.util.HashMap<>();
        final List<MavenProject> updatedProjects = new java.util.ArrayList<>();

        for (final MavenProject project : projects) {
            final File projectDir;
            if (project.getAbsolutePath() != null) {
                projectDir = new File(project.getAbsolutePath());
            } else {
                final String normalizedRelativePath = project.getRelativePath().replace('/', File.separatorChar);
                projectDir = new File(new File(workspace.getBasePath()), normalizedRelativePath);
            }

            final File gitRoot = findGitRoot(projectDir);
            final File cacheKey = gitRoot != null ? gitRoot : projectDir;

            final String branch = gitRootBranchCache.computeIfAbsent(cacheKey, key -> gitService.getCurrentBranch(key));
            project.setGitBranch(branch);
            updatedProjects.add(project);
        }

        if (!updatedProjects.isEmpty()) {
            mavenProjectRepository.saveAll(updatedProjects);
        }
    }

    /**
     * Triggers a re-scan of the workspace projects.
     * 
     * @param workspaceId The workspace ID.
     */
    @Transactional
    public void refreshWorkspace(final Long workspaceId) {
        final Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow();
        scanAndImportProjects(workspace);
    }

    /**
     * Updates the execution order of projects in a workspace.
     * 
     * @param workspaceId The workspace ID.
     * @param projectIds  The list of project IDs in their new order.
     */
    @Transactional
    public void updateProjectOrder(final Long workspaceId, final List<Long> projectIds) {
        int order = 0;
        for (final Long projectId : projectIds) {
            final Optional<MavenProject> opt = mavenProjectRepository.findById(projectId);
            if (opt.isPresent()) {
                final MavenProject project = opt.get();
                if (project.getWorkspace().getId().equals(workspaceId)) {
                    project.setExecutionOrder(order++);
                    mavenProjectRepository.save(project);
                }
            }
        }
    }

    /**
     * Checks if one project is a physical descendant of another.
     * 
     * @param child  The potential child project.
     * @param parent The potential parent project.
     * @return True if child is inside parent's directory.
     */
    public boolean isChildOf(final MavenProject child, final MavenProject parent) {
        if (child.getId().equals(parent.getId())) {
            return false;
        }

        final String parentPath = parent.getRelativePath().replace('\\', '/');
        final String childPath = child.getRelativePath().replace('\\', '/');

        if (parentPath.equals(".")) {
            // Everything else in the workspace is a child of the root project
            return true;
        }

        // Child path must start with parent path + "/"
        return childPath.startsWith(parentPath + "/");
    }

    /**
     * Checks if one project is a direct physical descendant of another.
     * 
     * @param child       The potential child project.
     * @param parent      The potential parent project.
     * @param allProjects The list of all projects in the workspace.
     * @return True if parent is the closest ancestor of child.
     */
    public boolean isDirectChildOf(final MavenProject child, final MavenProject parent,
            final List<MavenProject> allProjects) {
        if (!isChildOf(child, parent)) {
            return false;
        }

        // A child is direct if there is no intermediate project in the path between
        // them
        for (final MavenProject other : allProjects) {
            if (isChildOf(child, other) && isChildOf(other, parent)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Finds the Git repository root directory for a given directory.
     * Searches upwards until a directory containing a ".git" file/folder is found.
     * 
     * @param dir The starting directory.
     * @return The Git repository root directory, or null if not found.
     */
    private File findGitRoot(final File dir) {
        File current = dir;
        while (current != null) {
            final File gitDir = new File(current, ".git");
            if (gitDir.exists()) {
                return current;
            }
            current = current.getParentFile();
        }
        return null;
    }

    /**
     * Exports a workspace configuration as a plain text string.
     * 
     * @param workspaceId The ID of the workspace to export.
     * @return A plain text representation of the workspace.
     */
    @Transactional(readOnly = true)
    public String exportWorkspace(final Long workspaceId) {
        final Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow();
        final StringBuilder sb = new StringBuilder();
        
        sb.append("Workspace: ").append(workspace.getName()).append("\n");
        if (workspace.getBasePath() != null) {
            sb.append("BasePath: ").append(workspace.getBasePath()).append("\n");
        }
        for (final String excludedPath : workspace.getExcludedPaths()) {
            sb.append("Exclude: ").append(excludedPath).append("\n");
        }
        sb.append("\n");
        sb.append("# Projects in execution order (one path per line)\n");
        
        final List<MavenProject> projects = mavenProjectRepository.findByWorkspaceIdOrderByExecutionOrderAsc(workspaceId);
        for (final MavenProject project : projects) {
            if (project.getAbsolutePath() != null) {
                sb.append(project.getAbsolutePath()).append("\n");
            } else if (project.getRelativePath() != null && workspace.getBasePath() != null) {
                final File absFile = new File(new File(workspace.getBasePath()), project.getRelativePath());
                sb.append(absFile.getAbsolutePath()).append("\n");
            }
        }
        
        return sb.toString();
    }

    /**
     * Imports a workspace from a plain text configuration string.
     * 
     * @param textContent The plain text configuration.
     * @return The created workspace.
     */
    @Transactional
    public Workspace importWorkspace(final String textContent) {
        String name = "Imported Workspace";
        String basePath = null;
        final List<String> excludedPaths = new ArrayList<>();
        final List<String> projectPaths = new ArrayList<>();
        
        final String[] lines = textContent.split("\\r?\\n");
        for (final String line : lines) {
            final String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            
            if (trimmed.startsWith("Workspace:")) {
                name = trimmed.substring("Workspace:".length()).trim();
            } else if (trimmed.startsWith("BasePath:")) {
                basePath = trimmed.substring("BasePath:".length()).trim();
            } else if (trimmed.startsWith("Exclude:")) {
                excludedPaths.add(trimmed.substring("Exclude:".length()).trim());
            } else {
                projectPaths.add(trimmed);
            }
        }
        
        Workspace workspace = Workspace.builder()
                .withName(name)
                .withBasePath(basePath)
                .build();
        workspace.getExcludedPaths().addAll(excludedPaths);
        workspace = workspaceRepository.save(workspace);
        
        final java.util.Map<File, String> gitRootBranchCache = new java.util.HashMap<>();
        int executionOrder = 0;
        for (final String path : projectPaths) {
            File projectDir = new File(path);
            if (!projectDir.isAbsolute() && basePath != null) {
                projectDir = new File(new File(basePath), path);
            }
            
            final File pomFile = new File(projectDir, "pom.xml");
            if (pomFile.exists()) {
                final MavenProject projectData = mavenService.parsePom(pomFile, workspace.getBasePath());
                projectData.setWorkspace(workspace);
                projectData.setExecutionOrder(executionOrder++);
                
                final File gitRoot = findGitRoot(projectDir);
                final File cacheKey = gitRoot != null ? gitRoot : projectDir;
                final String branch = gitRootBranchCache.computeIfAbsent(cacheKey, key -> gitService.getCurrentBranch(key));
                projectData.setGitBranch(branch);
                
                mavenProjectRepository.save(projectData);
            }
        }
        
        return workspace;
    }
}
