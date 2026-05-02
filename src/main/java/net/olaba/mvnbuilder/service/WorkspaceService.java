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
                importProject(workspace, pomFile);
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
        final File pomFile = new File(projectDir, "pom.xml");
        if (pomFile.exists()) {
            importProject(workspace, pomFile);
        }
    }

    /**
     * Imports or updates a single Maven project into a workspace.
     * 
     * @param workspace The workspace.
     * @param pomFile   The pom.xml file.
     */
    private void importProject(final Workspace workspace, final File pomFile) {
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
        } else {
            projectToSave = projectData;
            projectToSave.setWorkspace(workspace);
        }

        projectToSave.setGitBranch(gitService.getCurrentBranch(pomFile.getParentFile()));
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
                if (file.isDirectory() && !file.getName().startsWith(".")) {
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

        final List<MavenProject> projects = getProjectsForWorkspace(workspaceId, false);
        for (final MavenProject project : projects) {
            // Ensure we use the correct path separator when building the absolute path
            final String normalizedRelativePath = project.getRelativePath().replace('/', File.separatorChar);
            final File projectDir = new File(new File(workspace.getBasePath()), normalizedRelativePath);
            project.setGitBranch(gitService.getCurrentBranch(projectDir));
            mavenProjectRepository.save(project);
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
}
