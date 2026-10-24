package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.*;
import net.olaba.mvnbuilder.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManualProjectsTest {
    @TempDir Path base;
    WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    MavenProjectRepository projects = mock(MavenProjectRepository.class);
    MavenService maven = new MavenService();
    GitService git = mock(GitService.class);
    BuildProfileRepository profiles = mock(BuildProfileRepository.class);
    WorkspaceService service = new WorkspaceService(workspaces, projects, maven, git, profiles);
    Workspace workspace;
    List<MavenProject> stored = new ArrayList<>();
    AtomicLong ids = new AtomicLong();
    @BeforeEach void setup() {
        workspace = Workspace.builder().withId(1L).withName("Mixed").withBasePath(base.toString()).build();
        when(workspaces.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaces.save(any())).thenAnswer(i -> { Workspace w=i.getArgument(0); if(w.getId()==null)w.setId(1L); return w; });
        when(projects.findByWorkspaceIdOrderByExecutionOrderAsc(1L)).thenAnswer(i -> List.copyOf(stored));
        when(projects.saveAll(any())).thenAnswer(i -> {
            Iterable<MavenProject> values=i.getArgument(0);
            for(MavenProject p:values) { if(p.getId()==null)p.setId(ids.incrementAndGet()); if(!stored.contains(p))stored.add(p); }
            return new ArrayList<>(stored);
        });
    }
    void pom(Path directory,String name) throws Exception {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("pom.xml"),"<project><modelVersion>4.0.0</modelVersion><groupId>test</groupId><artifactId>"+name+"</artifactId><version>1</version></project>");
    }
    @Test void defaultScanIsMavenOnlyAndOptInAddsExactlyOneFolder() throws Exception {
        pom(base.resolve("library"),"library"); Files.createDirectory(base.resolve("web"));
        service.scanAndImportProjects(workspace);
        assertEquals(1,stored.size()); assertTrue(stored.getFirst().isMaven());
        assertThrows(IllegalArgumentException.class,()->service.addProjectByPath(1L,base.resolve("web").toString()));
        service.addProjectByPath(1L,base.resolve("web").toString(),true);
        MavenProject folder=stored.getLast(); assertFalse(folder.isMaven()); assertEquals("web",folder.getDisplayName()); assertNull(folder.getArtifactId());
        service.addProjectByPath(1L,base.resolve("web/../web").toString(),true);
        assertEquals(2,stored.size());
    }
    @Test void folderRefreshPreservesIdentityAndMavenDescendantsRemainRoots() throws Exception {
        Path folder=Files.createDirectory(base.resolve("web"));
        service.addProjectByPath(1L,folder.toString(),true); MavenProject original=stored.getFirst(); original.setEnabled(false);
        pom(folder,"web"); pom(folder.resolve("module"),"module"); service.scanAndImportProjects(workspace);
        assertFalse(original.isMaven()); assertFalse(original.isEnabled()); assertEquals(2,stored.size());
        assertEquals(2,service.getProjectHierarchyForWorkspace(1L).topLevelProjects().size());
        service.addProjectByPath(1L,folder.toString(),false);
        assertTrue(original.isMaven()); assertEquals(2,stored.size()); assertFalse(original.isEnabled());
    }
    @Test void missingFolderRemainsRegisteredAndInvalidPathsAreRejected() throws Exception {
        Path folder=Files.createDirectory(base.resolve("web")); service.addProjectByPath(1L,folder.toString(),true); Files.delete(folder);
        service.scanAndImportProjects(workspace); assertEquals(1,stored.size()); assertFalse(stored.getFirst().isAvailable());
        Path file=Files.writeString(base.resolve("file"),"data"); assertThrows(IllegalArgumentException.class,()->service.addProjectByPath(1L,file.toString(),true));
        Path admin=Files.createDirectories(base.resolve("repo/.git/objects")); assertThrows(IllegalArgumentException.class,()->service.addProjectByPath(1L,admin.toString(),true));
    }
    @Test void mixedExportImportRetainsOrderAndExplicitKindEvenAfterPomAppears() throws Exception {
        pom(base.resolve("maven"),"library"); Path folder=Files.createDirectory(base.resolve("web"));
        service.addProjectByPath(1L,folder.toString(),true); service.addProjectByPath(1L,base.resolve("maven").toString());
        String exported=service.exportWorkspace(1L); assertTrue(exported.contains("NonMavenProject: "+folder));
        pom(folder,"new-pom"); stored.clear(); service.importWorkspace(exported);
        assertEquals(List.of(ProjectKind.FOLDER,ProjectKind.MAVEN),stored.stream().map(MavenProject::getProjectKind).toList());
        assertEquals(List.of(0,1),stored.stream().map(MavenProject::getExecutionOrder).toList());
    }
    @Test void legacyImportDoesNotInferFoldersAndReportsUnavailableEntries() throws Exception {
        Files.createDirectory(base.resolve("web")); pom(base.resolve("maven"),"library");
        Workspace imported=service.importWorkspace("Workspace: Legacy\nBasePath: "+base+"\nweb\nmaven\nmaven\nmissing\n");
        assertEquals(1,stored.size()); assertTrue(stored.getFirst().isMaven()); assertEquals(2,imported.getImportWarnings().size());
    }
    @Test void folderDeletionDoesNotDeleteNestedMavenProjectsOrExcludeTheirPath() throws Exception {
        Path folder=Files.createDirectory(base.resolve("web")); service.addProjectByPath(1L,folder.toString(),true);
        MavenProject registration=stored.getFirst(); when(projects.findById(registration.getId())).thenReturn(Optional.of(registration));
        pom(folder.resolve("module"),"module"); service.scanAndImportProjects(workspace); service.deleteProject(registration.getId());
        verify(projects).deleteAll(List.of(registration)); assertTrue(workspace.getExcludedPaths().isEmpty());
    }
    @Test void autoOrderingKeepsFolderSlotAndSortsMavenDependencies() {
        MavenProject dependent=MavenProject.builder().withId(1L).withArtifactId("dependent").withGroupId("test")
                .withRelativePath("dependent").withExecutionOrder(0).withInternalDependencies(List.of("test:base")).build();
        MavenProject folder=MavenProject.builder().withId(2L).withProjectKind(ProjectKind.FOLDER).withRelativePath("web").withExecutionOrder(1).build();
        MavenProject dependency=MavenProject.builder().withId(3L).withArtifactId("base").withGroupId("test").withRelativePath("base").withExecutionOrder(2).build();
        stored.addAll(List.of(dependent,folder,dependency)); new TopologicalSortService(projects,service).calculateOrder(1L);
        assertEquals(1,folder.getExecutionOrder()); assertEquals(0,dependency.getExecutionOrder()); assertEquals(2,dependent.getExecutionOrder());
    }
}
