package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.model.WorkspaceProjectHierarchy;
import net.olaba.mvnbuilder.repository.BuildProfileRepository;
import net.olaba.mvnbuilder.repository.MavenProjectRepository;
import net.olaba.mvnbuilder.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Parity checks for the indexed hierarchy and pruned workspace scan. */
class WorkspaceHierarchyRefreshTest {

    @TempDir Path temporaryDirectory;

    /** Nested and external projects retain IDs, flags, order, and legacy hierarchy semantics. */
    @Test
    void refreshPreservesNestedAndExternalProjects() throws IOException {
        final Path base = temporaryDirectory.resolve("base");
        final Path external = temporaryDirectory.resolve("external");
        writePom(base.resolve("parent"), "parent");
        writePom(base.resolve("parent/module"), "module");
        writePom(base.resolve("sibling"), "sibling");
        writePom(base.resolve("excluded/ignored"), "ignored");
        writePom(external, "external");
        writePom(external.resolve("module"), "external-module");
        final Workspace workspace = Workspace.builder().withId(1L).withBasePath(base.toString()).build();
        workspace.getExcludedPaths().add("excluded");
        final MavenProject externalParent = MavenProject.builder().withId(90L)
                .withRelativePath("../external").withAbsolutePath(external.toString())
                .withWorkspace(workspace).withEnabled(false).withExecutionOrder(8).build();
        final AtomicReference<List<MavenProject>> stored = new AtomicReference<>(List.of(externalParent));
        final WorkspaceService service = serviceWithStoredProjects(workspace, stored);

        service.refreshWorkspace(1L);
        final List<MavenProject> first = stored.get();
        assertEquals(Set.of("parent", "parent/module", "sibling", "../external", "../external/module"),
                paths(first));
        assertEquals(90L, first.stream().filter(p -> p.getRelativePath().equals("../external"))
                .findFirst().orElseThrow().getId());
        assertFalse(externalParent.isEnabled());
        assertEquals(8, externalParent.getExecutionOrder());
        assertMatchesLegacyHierarchy(service, workspace, first);

        final MavenProject parent = first.stream().filter(p -> p.getRelativePath().equals("parent"))
                .findFirst().orElseThrow();
        parent.setEnabled(false);
        parent.setExecutionOrder(3);
        service.refreshWorkspace(1L);
        final MavenProject refreshedParent = stored.get().stream()
                .filter(p -> p.getRelativePath().equals("parent")).findFirst().orElseThrow();
        assertEquals(parent.getId(), refreshedParent.getId());
        assertFalse(refreshedParent.isEnabled());
        assertEquals(3, refreshedParent.getExecutionOrder());
        assertMatchesLegacyHierarchy(service, workspace, stored.get());
    }

    /** A root POM remains the only top-level row, even with nested modules. */
    @Test
    void rootPomKeepsLegacyTopLevelRule() throws IOException {
        final Path base = temporaryDirectory.resolve("root-workspace");
        writePom(base, "root");
        writePom(base.resolve("module"), "module");
        final Workspace workspace = Workspace.builder().withId(2L).withBasePath(base.toString()).build();
        final AtomicReference<List<MavenProject>> stored = new AtomicReference<>(List.of());
        final WorkspaceService service = serviceWithStoredProjects(workspace, stored);

        service.refreshWorkspace(2L);

        assertEquals(Set.of(".", "module"), paths(stored.get()));
        assertEquals(List.of("."), service.getProjectHierarchyForWorkspace(2L).topLevelProjects()
                .stream().map(MavenProject::getRelativePath).toList());
        assertMatchesLegacyHierarchy(service, workspace, stored.get());
    }

    /** Excluding only the root POM still permits ordinary child discovery. */
    @Test
    void rootExclusionDoesNotPruneChildren() throws IOException {
        final Path base = temporaryDirectory.resolve("excluded-root");
        writePom(base, "root");
        writePom(base.resolve("module"), "module");
        final Workspace workspace = Workspace.builder().withId(3L).withBasePath(base.toString()).build();
        workspace.getExcludedPaths().add(".");
        final AtomicReference<List<MavenProject>> stored = new AtomicReference<>(List.of());
        final WorkspaceService service = serviceWithStoredProjects(workspace, stored);

        service.refreshWorkspace(3L);

        assertEquals(Set.of("module"), paths(stored.get()));
        assertMatchesLegacyHierarchy(service, workspace, stored.get());
    }

    /** The rescan reads only the filesystem; stored branches wait for the Git phase. */
    @Test
    void rescanNeverRunsGitAndKeepsStoredBranches() throws IOException {
        final Path base = temporaryDirectory.resolve("no-git");
        writePom(base.resolve("api"), "api");
        writePom(base.resolve("lib"), "lib");
        Files.createDirectories(temporaryDirectory.resolve("docs"));
        final Workspace workspace = Workspace.builder().withId(4L).withBasePath(base.toString()).build();
        final MavenProject api = MavenProject.builder().withId(40L).withRelativePath("api")
                .withAbsolutePath(base.resolve("api").toString()).withWorkspace(workspace)
                .withGitBranch("feature/x").build();
        final MavenProject docs = MavenProject.builder().withId(41L).withRelativePath("../docs")
                .withAbsolutePath(temporaryDirectory.resolve("docs").toString()).withWorkspace(workspace)
                .withProjectKind(net.olaba.mvnbuilder.entities.ProjectKind.FOLDER).withGitBranch("release").build();
        final AtomicReference<List<MavenProject>> stored = new AtomicReference<>(List.of(api, docs));
        final GitService git = mock(GitService.class);
        final WorkspaceService service = serviceWithStoredProjects(workspace, stored, git);

        service.refreshWorkspace(4L);

        verifyNoInteractions(git);
        assertEquals(Set.of("api", "lib", "../docs"), paths(stored.get()));
        assertEquals("feature/x", branch(stored.get(), "api"));
        assertEquals("release", branch(stored.get(), "../docs"));
        assertNull(branch(stored.get(), "lib"), "new rows get their branch from the Git phase");
    }

    private static String branch(final List<MavenProject> projects, final String path) {
        return projects.stream().filter(p -> p.getRelativePath().equals(path)).findFirst().orElseThrow().getGitBranch();
    }

    /** Compares the optimized summary against the previous all-pairs definitions. */
    private void assertMatchesLegacyHierarchy(final WorkspaceService service, final Workspace workspace,
            final List<MavenProject> projects) {
        workspace.getProjects().clear();
        workspace.getProjects().addAll(projects);
        final WorkspaceProjectHierarchy hierarchy = service.getProjectHierarchyForWorkspace(workspace.getId());
        final List<MavenProject> legacyTopLevel = projects.stream()
                .filter(project -> projects.stream().noneMatch(parent -> service.isChildOf(project, parent)))
                .toList();
        assertEquals(legacyTopLevel, hierarchy.topLevelProjects());
        final Set<Long> legacyParents = projects.stream().filter(MavenProject::hasChildren)
                .map(MavenProject::getId).collect(Collectors.toSet());
        assertEquals(legacyParents, hierarchy.projectIdsWithChildren());
    }

    /** Supplies an in-memory repository that keeps assigned IDs across rescans. */
    private WorkspaceService serviceWithStoredProjects(final Workspace workspace,
            final AtomicReference<List<MavenProject>> stored) {
        final GitService gitService = mock(GitService.class);
        when(gitService.getCurrentBranch(any(File.class))).thenReturn("main");
        return serviceWithStoredProjects(workspace, stored, gitService);
    }

    private WorkspaceService serviceWithStoredProjects(final Workspace workspace,
            final AtomicReference<List<MavenProject>> stored, final GitService gitService) {
        final WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        final MavenProjectRepository projectRepository = mock(MavenProjectRepository.class);
        final BuildProfileRepository profileRepository = mock(BuildProfileRepository.class);
        final AtomicLong nextId = new AtomicLong(100);
        when(workspaceRepository.findById(workspace.getId())).thenReturn(java.util.Optional.of(workspace));
        when(projectRepository.findByWorkspaceIdOrderByExecutionOrderAsc(anyLong()))
                .thenAnswer(invocation -> stored.get());
        when(projectRepository.saveAll(any())).thenAnswer(invocation -> {
            final List<MavenProject> saved = new ArrayList<>(invocation.getArgument(0));
            for (final MavenProject project : saved) {
                if (project.getId() == null) project.setId(nextId.getAndIncrement());
                project.setWorkspace(workspace);
            }
            stored.set(saved);
            return saved;
        });
        return new WorkspaceService(workspaceRepository, projectRepository,
                new MavenService(), gitService, profileRepository);
    }

    /** Writes a minimal valid POM to a fixture directory. */
    private void writePom(final Path directory, final String artifactId) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion>"
                + "<groupId>test</groupId><artifactId>" + artifactId
                + "</artifactId><version>1</version></project>");
    }

    /** Returns normalized stored relative paths for membership assertions. */
    private Set<String> paths(final List<MavenProject> projects) {
        return projects.stream().map(MavenProject::getRelativePath).collect(Collectors.toSet());
    }
}
