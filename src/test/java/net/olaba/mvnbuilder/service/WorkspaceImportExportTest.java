package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.repository.MavenProjectRepository;
import net.olaba.mvnbuilder.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkspaceImportExportTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private MavenProjectRepository mavenProjectRepository;

    @Mock
    private MavenService mavenService;

    @Mock
    private GitService gitService;

    @InjectMocks
    private WorkspaceService workspaceService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testExportWorkspace() {
        Workspace ws = Workspace.builder()
                .withId(1L)
                .withName("My Test Workspace")
                .withBasePath("/tmp/base")
                .build();
        ws.getExcludedPaths().add("excluded/path1");

        MavenProject p1 = MavenProject.builder()
                .withId(10L)
                .withArtifactId("proj1")
                .withAbsolutePath("/tmp/base/proj1")
                .build();

        MavenProject p2 = MavenProject.builder()
                .withId(11L)
                .withArtifactId("proj2")
                .withRelativePath("proj2")
                .build();

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(ws));
        when(mavenProjectRepository.findByWorkspaceIdOrderByExecutionOrderAsc(1L)).thenReturn(Arrays.asList(p1, p2));

        String exported = workspaceService.exportWorkspace(1L).replace('\\', '/');

        assertTrue(exported.contains("Workspace: My Test Workspace"));
        assertTrue(exported.contains("BasePath: /tmp/base"));
        assertTrue(exported.contains("Exclude: excluded/path1"));
        assertTrue(exported.contains("/tmp/base/proj1"));
        assertTrue(exported.contains("/tmp/base/proj2"));
    }

    @Test
    void testImportWorkspaceWithFileSystem() throws Exception {
        java.nio.file.Path tempBase = java.nio.file.Files.createTempDirectory("workspace-test-base");
        java.nio.file.Path tempProj1 = java.nio.file.Files.createDirectory(tempBase.resolve("proj1"));
        java.nio.file.Files.createFile(tempProj1.resolve("pom.xml"));

        String textConfig = "Workspace: Imported Test WS\n" +
                "BasePath: " + tempBase.toAbsolutePath().toString() + "\n" +
                "Exclude: path/to/exclude1\n" +
                "\n" +
                tempProj1.toAbsolutePath().toString() + "\n";

        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> {
            Workspace ws = invocation.getArgument(0);
            ws.setId(99L);
            return ws;
        });

        MavenProject parsedProj = MavenProject.builder()
                .withArtifactId("proj1")
                .withGroupId("com.test")
                .withVersion("1.0")
                .build();
        when(mavenService.parsePom(any(File.class), any())).thenReturn(parsedProj);
        when(gitService.getCurrentBranch(any())).thenReturn("main");

        Workspace imported = workspaceService.importWorkspace(textConfig);

        assertNotNull(imported);
        assertEquals("Imported Test WS", imported.getName());
        assertEquals(tempBase.toAbsolutePath().toString(), imported.getBasePath());
        assertEquals(1, imported.getExcludedPaths().size());
        assertEquals("path/to/exclude1", imported.getExcludedPaths().get(0));

        verify(mavenProjectRepository, times(1)).saveAll(any());
        
        // Clean up temp files
        java.nio.file.Files.delete(tempProj1.resolve("pom.xml"));
        java.nio.file.Files.delete(tempProj1);
        java.nio.file.Files.delete(tempBase);
    }
}
