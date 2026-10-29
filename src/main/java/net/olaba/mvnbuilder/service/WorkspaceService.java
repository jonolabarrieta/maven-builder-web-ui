package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.entities.ProjectKind;
import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.model.WorkspaceProjectHierarchy;
import net.olaba.mvnbuilder.repository.MavenProjectRepository;
import net.olaba.mvnbuilder.repository.BuildProfileRepository;
import net.olaba.mvnbuilder.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing workspaces and their associated Maven projects.
 */
@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class WorkspaceService {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private WorkspaceGroupService groups;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private WorkspaceExchangeService exchange;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private net.olaba.mvnbuilder.repository.RunEntryRepository runEntries;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private RunProcessRegistry runProcesses;

    public String currentBranch(File directory) { return gitService.getCurrentBranch(directory); }

    /** Shared by every Git status request so concurrent pages cannot multiply Git processes. */
    private final java.util.concurrent.ThreadPoolExecutor branchReaders = new java.util.concurrent.ThreadPoolExecutor(
            6, 6, 0, java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.LinkedBlockingQueue<>(),
            Thread.ofVirtual().name("git-branch-", 0).factory());

    @org.springframework.beans.factory.annotation.Value("${workspace.git.branch-workers:6}")
    private int branchWorkerCount = 6;

    @jakarta.annotation.PostConstruct
    void configureBranchReaders() {
        final int size = Math.max(1, branchWorkerCount);
        branchReaders.setCorePoolSize(Math.min(size, branchReaders.getCorePoolSize()));
        branchReaders.setMaximumPoolSize(size);
        branchReaders.setCorePoolSize(size);
    }

    int branchReaderLimit() { return branchReaders.getMaximumPoolSize(); }

    @jakarta.annotation.PreDestroy
    void closeBranchReaders() { branchReaders.shutdownNow(); }


    private final WorkspaceRepository workspaceRepository;
    private final MavenProjectRepository mavenProjectRepository;
    private final MavenService mavenService;
    private final GitService gitService;
    private final BuildProfileRepository buildProfileRepository;

    private String activeVersionProfileId() {
        return buildProfileRepository.findByIsDefaultTrue()
                .map(profile -> profile.getVersionProfileId()).orElse(null);
    }

    @Transactional
    public void refreshDisplayedVersions(final String versionProfileId) {
        final List<MavenProject> changed = new ArrayList<>();
        for (final MavenProject project : mavenProjectRepository.findAll()) {
            if (!project.isMaven()) continue;
            if (project.getAbsolutePath() == null) {
                continue;
            }
            final File pomFile = new File(project.getAbsolutePath(), "pom.xml");
            if (!pomFile.isFile()) {
                continue;
            }
            try {
                final String version = mavenService.resolveVersion(pomFile, versionProfileId);
                if (version != null && !version.equals(project.getVersion())) {
                    project.setVersion(version);
                    changed.add(project);
                }
            } catch (final RuntimeException ignored) {
                // Keep the last known version if this POM cannot be read.
            }
        }
        if (!changed.isEmpty()) {
            mavenProjectRepository.saveAll(changed);
        }
    }

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
            if (groups != null) groups.projectRemoved(id, mavenProjectRepository.findByWorkspaceIdOrderByExecutionOrderAsc(id).stream().map(MavenProject::getId).toList());
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
        if (groups != null) groups.beforeDelete(id);
        if (runProcesses != null) runProcesses.stopWorkspace(id);
        if (runEntries != null) runEntries.deleteAll(runEntries.findByWorkspaceIdOrderByPositionAscIdAsc(id));
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
        final Map<Long, Long> copiedIds = new HashMap<>();

        Workspace copy = Workspace.builder()
                .withName(original.getName() + " (Copy)")
                .withKind(original.getKind())
                .withJavaInstallation(original.getJavaInstallation())
                .withBasePath(original.getBasePath())
                .build();
        copy.getExcludedPaths().addAll(original.getExcludedPaths());
        copy = workspaceRepository.save(copy);

        final List<MavenProject> projects = mavenProjectRepository.findByWorkspaceIdOrderByExecutionOrderAsc(id);
        for (final MavenProject originalProject : projects) {
            MavenProject projectCopy = MavenProject.builder()
                    .withProjectKind(originalProject.getProjectKind())
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
            projectCopy = mavenProjectRepository.save(projectCopy);
            copiedIds.put(originalProject.getId(), projectCopy.getId());
        }

        if (groups != null) groups.copyLayout(id, copy, copiedIds);
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
        if (project.isMaven() && !workspace.getExcludedPaths().contains(project.getRelativePath())) {
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

        if (groups != null) groups.projectRemoved(workspaceId, toDelete.stream().map(MavenProject::getId).toList());
        mavenProjectRepository.deleteAll(toDelete);
    }

    /**
     * Scans the workspace base path for pom.xml files and imports them.
     * 
     * @param workspace The workspace to scan.
     */
    public void scanAndImportProjects(final Workspace workspace) {
        if (workspace.getBasePath() == null) {
            return;
        }
        final File baseDir = new File(workspace.getBasePath());
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            return; // Don't throw exception here, just don't import
        }

        final Set<String> excludedPaths = new HashSet<>(workspace.getExcludedPaths());
        final List<File> pomFiles = new ArrayList<>();
        findPomFiles(baseDir, pomFiles, baseDir, excludedPaths);
        final String versionProfileId = activeVersionProfileId();

        // Branches are read by the Git phase that follows a rescan, never here.

        // Load existing projects for the workspace first in a single query
        final List<MavenProject> existingProjects = mavenProjectRepository
                .findByWorkspaceIdOrderByExecutionOrderAsc(workspace.getId());
        final java.util.Map<String, MavenProject> existingMap = existingProjects.stream()
                .collect(Collectors.toMap(MavenProject::getRelativePath, p -> p));
        final Map<String, MavenProject> existingDirectories = new HashMap<>();
        for (MavenProject project : existingProjects) if (project.getAbsolutePath() != null)
            existingDirectories.put(canonical(new File(project.getAbsolutePath())), project);

        final List<MavenProject> projectsToSave = new ArrayList<>();

        for (MavenProject folder : existingProjects) {
            if (!folder.isMaven()) {
                projectsToSave.add(folder);
            }
        }

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
                final MavenProject projectData = mavenService.parsePom(pomFile, workspace.getBasePath(), versionProfileId);
                final MavenProject existing = existingMap.getOrDefault(projectData.getRelativePath(), existingDirectories.get(canonical(pomFile.getParentFile())));
                if (existing != null && !existing.isMaven()) continue;

                final MavenProject projectToSave;
                if (existing != null) {
                    projectToSave = existing;
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

                projectsToSave.add(projectToSave);
            }
        }

        // Scan manually added parent projects that reside outside the base path
        final String baseAbsNormalized;
        try {
            baseAbsNormalized = baseDir.getAbsoluteFile().toPath().normalize().toString();
        } catch (final Exception e) {
            return;
        }

        final List<MavenProject> externalParents = summarizeHierarchy(existingProjects).topLevelProjects().stream()
                .filter(MavenProject::isMaven)
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
                    final MavenProject projectData = mavenService.parsePom(pomFile, workspace.getBasePath(), versionProfileId);
                    final MavenProject existing = existingMap.getOrDefault(projectData.getRelativePath(), existingDirectories.get(canonical(pomFile.getParentFile())));
                    if (existing != null && !existing.isMaven()) continue;

                    final MavenProject projectToSave;
                    if (existing != null) {
                        projectToSave = existing;
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

                    projectsToSave.add(projectToSave);
                }
            }
        }

        if (!projectsToSave.isEmpty()) {
            mavenProjectRepository.saveAll(projectsToSave);
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
        addProjectByPath(workspaceId, projectPath, false);
    }

    @Transactional
    public void addProjectByPath(final Long workspaceId, final String projectPath, final boolean includeNonMaven) {
        final Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow();
        final File directory = validatedDirectory(projectPath);
        final List<MavenProject> existing = mavenProjectRepository.findByWorkspaceIdOrderByExecutionOrderAsc(workspaceId);
        final Map<String, MavenProject> byDirectory = new HashMap<>();
        for (MavenProject project : existing) if (project.getAbsolutePath() != null)
            byDirectory.put(canonical(new File(project.getAbsolutePath())), project);
        final List<File> poms = new ArrayList<>();
        boolean folder = includeNonMaven && !new File(directory, "pom.xml").isFile();
        if (!folder) findPomFiles(directory, poms);
        if (!folder && poms.isEmpty()) throw new IllegalArgumentException("No Maven projects found. Enable Include non-Maven folders to add this directory.");
        final List<MavenProject> saved = new ArrayList<>();
        int nextOrder = existing.stream().map(MavenProject::getExecutionOrder).filter(java.util.Objects::nonNull).max(Integer::compare).orElse(-1) + 1;
        List<File> directories = folder ? List.of(directory) : poms.stream().map(File::getParentFile).toList();
        for (File entry : directories) {
            String key = canonical(entry);
            MavenProject previous = byDirectory.get(key);
            if (folder && previous != null) continue;
            MavenProject data = folder ? folderProject(workspace, entry) : mavenService.parsePom(new File(entry, "pom.xml"), workspace.getBasePath(), activeVersionProfileId());
            MavenProject project = previous == null ? data : previous;
            if (previous != null) {
                project.setProjectKind(ProjectKind.MAVEN);
                project.setArtifactId(data.getArtifactId()); project.setGroupId(data.getGroupId()); project.setVersion(data.getVersion());
                project.setParentKey(data.getParentKey()); project.setModules(data.getModules()); project.setInternalDependencies(data.getInternalDependencies());
                project.setAbsolutePath(data.getAbsolutePath()); project.setRelativePath(data.getRelativePath());
            } else { project.setWorkspace(workspace); project.setExecutionOrder(nextOrder++); }
            project.setGitBranch(gitService.getCurrentBranch(entry));
            saved.add(project); byDirectory.put(key, project);
        }
        if (!saved.isEmpty()) mavenProjectRepository.saveAll(saved);
    }

    private File validatedDirectory(String path) {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("Choose an existing project directory.");
        File directory = new File(canonical(new File(path)));
        if (!directory.isDirectory() || !directory.canRead()) throw new IllegalArgumentException("Project directory is unavailable: " + path);
        for (java.nio.file.Path part : directory.toPath()) if (part.toString().equalsIgnoreCase(".git"))
            throw new IllegalArgumentException("Git administrative directories cannot be registered as projects.");
        return directory;
    }
    private String canonical(File directory) {
        try { return directory.getCanonicalPath(); }
        catch (java.io.IOException e) { throw new IllegalArgumentException("Cannot resolve project directory: " + directory, e); }
    }
    private MavenProject folderProject(Workspace workspace, File directory) {
        String absolute = canonical(directory);
        String relative = absolute;
        if (workspace.getBasePath() != null) {
            try { relative = new File(workspace.getBasePath()).getAbsoluteFile().toPath().normalize().relativize(new File(absolute).toPath()).toString(); }
            catch (IllegalArgumentException differentDrive) { relative = absolute; }
        }
        if (relative.isEmpty()) relative = ".";
        return MavenProject.builder().withProjectKind(ProjectKind.FOLDER).withAbsolutePath(absolute)
                .withRelativePath(relative.replace('\\', '/')).withWorkspace(workspace).build();
    }

    /**
     * Recursively finds all pom.xml files starting from a directory.
     * 
     * @param dir      The directory to search in.
     * @param pomFiles The list to accumulate found files.
     */
    private void findPomFiles(final File dir, final List<File> pomFiles) {
        findPomFiles(dir, pomFiles, null, Set.of());
    }

    /**
     * Finds POMs while pruning workspace exclusions before descending into them.
     * External manually added parents use the two-argument form without exclusions.
     *
     * @param dir current directory
     * @param pomFiles discovered POMs
     * @param baseDir workspace base directory, or null for external scans
     * @param excludedPaths configured relative paths
     */
    private void findPomFiles(final File dir, final List<File> pomFiles, final File baseDir,
            final Set<String> excludedPaths) {
        boolean excludePom = false;
        if (baseDir != null) {
            String relativePath = baseDir.toPath().relativize(dir.toPath()).toString().replace('\\', '/');
            if (relativePath.isEmpty()) {
                relativePath = ".";
            }
            for (final String excludedPath : excludedPaths) {
                final String normalized = excludedPath.replace('\\', '/');
                if (relativePath.equals(normalized) || relativePath.startsWith(normalized + "/")) {
                    if (!relativePath.equals(".")) {
                        return;
                    }
                    excludePom = true;
                }
            }
        }
        final File pom = new File(dir, "pom.xml");
        if (!excludePom && pom.exists()) {
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
                    findPomFiles(file, pomFiles, baseDir, excludedPaths);
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
            return summarizeHierarchy(projects).topLevelProjects();
        }
        return projects;
    }

    /**
     * Returns the top-level rows and child indicators from the same repository query.
     * 
     * @param workspaceId the workspace identifier
     * @return a hierarchy summary in execution order
     */
    public WorkspaceProjectHierarchy getProjectHierarchyForWorkspace(final Long workspaceId) {
        final List<MavenProject> projects = mavenProjectRepository
                .findByWorkspaceIdOrderByExecutionOrderAsc(workspaceId);
        return summarizeHierarchy(projects);
    }

    /**
     * Indexes stored paths so ancestry is checked by path depth rather than list size.
     *
     * @param allProjects the ordered project snapshot
     * @return top-level projects and identifiers with descendants
     */
    private WorkspaceProjectHierarchy summarizeHierarchy(final List<MavenProject> allProjects) {
        final Map<String, List<MavenProject>> byPath = new HashMap<>();
        for (final MavenProject project : allProjects) {
            if (project.isMaven() && project.getRelativePath() != null) {
                final String path = project.getRelativePath().replace('\\', '/');
                byPath.computeIfAbsent(path, ignored -> new ArrayList<>()).add(project);
            }
        }

        final List<MavenProject> topLevel = new ArrayList<>();
        final Set<Long> withChildren = new HashSet<>();
        for (final MavenProject project : allProjects) {
            if (!project.isMaven()) { topLevel.add(project); continue; }
            final String path = project.getRelativePath();
            if (path == null) {
                topLevel.add(project);
                continue;
            }
            final String normalized = path.replace('\\', '/');
            boolean hasAncestor = false;
            if (!normalized.equals(".")) {
                hasAncestor = markAncestors(project, byPath.get("."), withChildren);
            }
            for (int slash = normalized.indexOf('/'); slash >= 0;
                    slash = normalized.indexOf('/', slash + 1)) {
                hasAncestor |= markAncestors(project, byPath.get(normalized.substring(0, slash)), withChildren);
            }
            if (!hasAncestor) {
                topLevel.add(project);
            }
        }
        return new WorkspaceProjectHierarchy(topLevel, withChildren);
    }

    /**
     * Marks matching stored ancestors as parents of one project.
     *
     * @param project potential child
     * @param ancestors projects at one ancestor path, or null
     * @param withChildren accumulator of parent identifiers
     * @return whether at least one distinct ancestor exists
     */
    private boolean markAncestors(final MavenProject project, final List<MavenProject> ancestors,
            final Set<Long> withChildren) {
        if (ancestors == null) {
            return false;
        }
        boolean found = false;
        for (final MavenProject ancestor : ancestors) {
            if (ancestor.getId() != null && ancestor.getId().equals(project.getId())) {
                continue;
            }
            found = true;
            if (ancestor.getId() != null) {
                withChildren.add(ancestor.getId());
            }
        }
        return found;
    }

    /**
     * Refreshes the Git branch status for all projects in a workspace.
     * 
     * @param workspaceId The workspace ID.
     * @return the top-level projects with their refreshed branch values
     */
    public List<MavenProject> refreshGitStatus(final Long workspaceId) {
        return refreshGitStatus(List.of(workspaceId));
    }

    /**
     * Refreshes the branches of the top-level projects of several source workspaces in one pass.
     * Each distinct repository is read once, in parallel through the shared branch readers; a
     * project outside any repository gets {@code n/a} without starting Git.
     *
     * @param workspaceIds the source workspaces, typically a consumer and its imported parents
     * @return the top-level projects of every workspace with their refreshed branch values
     */
    public List<MavenProject> refreshGitStatus(final java.util.Collection<Long> workspaceIds) {
        final List<MavenProject> projects = new ArrayList<>();
        final List<MavenProject> updatedProjects = new ArrayList<>();
        final Map<File, List<MavenProject>> projectsByRoot = new java.util.LinkedHashMap<>();
        for (final Long workspaceId : new java.util.LinkedHashSet<>(workspaceIds)) {
            final Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow();
            for (final MavenProject project : getProjectsForWorkspace(workspaceId, true)) {
                projects.add(project);
                final File projectDir;
                if (project.getAbsolutePath() != null) {
                    projectDir = new File(project.getAbsolutePath());
                } else {
                    if (workspace.getBasePath() == null || project.getRelativePath() == null) continue;
                    final String normalizedRelativePath = project.getRelativePath().replace('/', File.separatorChar);
                    projectDir = new File(new File(workspace.getBasePath()), normalizedRelativePath);
                }
                final File gitRoot = findGitRoot(projectDir);
                if (gitRoot == null) {
                    project.setGitBranch("n/a");
                    updatedProjects.add(project);
                } else {
                    projectsByRoot.computeIfAbsent(gitRoot, key -> new ArrayList<>()).add(project);
                }
            }
        }

        final Map<File, java.util.concurrent.Future<String>> reads = new java.util.LinkedHashMap<>();
        for (final File root : projectsByRoot.keySet()) {
            reads.put(root, branchReaders.submit(() -> gitService.getCurrentBranch(root)));
        }
        // Every Git call is bounded by its own timeout; this only guards against a stuck queue.
        final int rounds = (reads.size() + branchReaders.getMaximumPoolSize() - 1) / branchReaders.getMaximumPoolSize();
        final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15L * (rounds + 1));
        for (final Map.Entry<File, java.util.concurrent.Future<String>> read : reads.entrySet()) {
            final String branch = awaitBranch(read.getKey(), read.getValue(), deadline);
            for (final MavenProject project : projectsByRoot.get(read.getKey())) {
                project.setGitBranch(branch);
                updatedProjects.add(project);
            }
        }

        if (!updatedProjects.isEmpty()) {
            mavenProjectRepository.saveAll(updatedProjects);
        }
        return projects;
    }

    private static String awaitBranch(final File root, final java.util.concurrent.Future<String> read, final long deadline) {
        try {
            return read.get(Math.max(0, deadline - System.nanoTime()), java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            read.cancel(true);
        } catch (final java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException e) {
            read.cancel(true);
            log.warn("Failed to read the Git branch of {}: {}", root, e.toString());
        }
        return "n/a";
    }

    /**
     * Triggers a re-scan of the workspace projects.
     * 
     * @param workspaceId The workspace ID.
     */
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
        if (groups != null) groups.synchronizeUngroupedOrder(workspaceId);
    }

    /**
     * Checks if one project is a physical descendant of another.
     * 
     * @param child  The potential child project.
     * @param parent The potential parent project.
     * @return True if child is inside parent's directory.
     */
    public boolean isChildOf(final MavenProject child, final MavenProject parent) {
        if (!parent.isMaven() || !child.isMaven()) return false;
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
        if (exchange != null && groups != null && (groups.workspace(workspaceId).isParent() || !groups.list(workspaceId).isEmpty()
                || (runEntries != null && !runEntries.findByWorkspaceIdOrderByPositionAscIdAsc(workspaceId).isEmpty()))) return exchange.export(workspaceId);
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
            if (!project.isMaven()) sb.append("NonMavenProject: ");
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
        if (textContent.startsWith("MvnBuilderWorkspace:")) {
            if (exchange == null) throw new IllegalArgumentException("Grouped workspace import is unavailable.");
            return exchange.read(textContent.replace("\r\n", "\n"));
        }
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
        final String versionProfileId = activeVersionProfileId();
        int executionOrder = 0;
        final List<MavenProject> projectsToSave = new ArrayList<>();

        Set<String> imported = new HashSet<>();
        for (final String entry : projectPaths) {
            boolean folder = entry.startsWith("NonMavenProject:");
            String path = folder ? entry.substring("NonMavenProject:".length()).trim() : entry;
            File projectDir = new File(path);
            if (!projectDir.isAbsolute() && basePath != null) {
                projectDir = new File(new File(basePath), path);
            }
            
            if (!projectDir.isDirectory() || !projectDir.canRead()) { workspace.getImportWarnings().add("Unavailable project directory: " + path); continue; }
            if (!imported.add(canonical(projectDir))) continue;
            if (folder) {
                try { projectDir = validatedDirectory(projectDir.getPath()); }
                catch (IllegalArgumentException e) { workspace.getImportWarnings().add(e.getMessage()); continue; }
            }
            final File pomFile = new File(projectDir, "pom.xml");
            if (folder || pomFile.isFile()) {
                final MavenProject projectData = folder ? folderProject(workspace, projectDir) : mavenService.parsePom(pomFile, workspace.getBasePath(), versionProfileId);
                projectData.setWorkspace(workspace);
                projectData.setExecutionOrder(executionOrder++);
                
                final File gitRoot = findGitRoot(projectDir);
                final File cacheKey = gitRoot != null ? gitRoot : projectDir;
                final String branch = gitRootBranchCache.computeIfAbsent(cacheKey, key -> gitService.getCurrentBranch(key));
                projectData.setGitBranch(branch);
                
                projectsToSave.add(projectData);
            } else workspace.getImportWarnings().add("No POM found; skipped Maven entry: " + path);
        }

        if (!projectsToSave.isEmpty()) {
            mavenProjectRepository.saveAll(projectsToSave);
        }
        
        return workspace;
    }
}
