package net.olaba.mvnbuilder.controller;

import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.repository.JavaInstallationRepository;
import net.olaba.mvnbuilder.repository.MavenProjectRepository;
import net.olaba.mvnbuilder.service.BuildService;
import net.olaba.mvnbuilder.service.DesktopLauncherService;
import net.olaba.mvnbuilder.service.FileSystemService;
import net.olaba.mvnbuilder.service.GitService;
import net.olaba.mvnbuilder.service.MavenRepositoryService;
import net.olaba.mvnbuilder.service.MavenService;
import net.olaba.mvnbuilder.service.SystemSettingService;
import net.olaba.mvnbuilder.service.TopologicalSortService;
import net.olaba.mvnbuilder.service.UpdateService;
import net.olaba.mvnbuilder.service.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class WorkspaceControllerExportPathsTest {

    @Mock private WorkspaceService workspaceService;
    @Mock private BuildService buildService;
    @Mock private TopologicalSortService topologicalSortService;
    @Mock private MavenProjectRepository mavenProjectRepository;
    @Mock private GitService gitService;
    @Mock private MavenRepositoryService mavenRepositoryService;
    @Mock private FileSystemService fileSystemService;
    @Mock private MavenService mavenService;
    @Mock private SystemSettingService systemSettingService;
    @Mock private JavaInstallationRepository javaInstallationRepository;
    @Mock private UpdateService updateService;
    @Mock private DesktopLauncherService desktopLauncherService;
    @InjectMocks private WorkspaceController workspaceController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void exportsOnlyOrderedAbsoluteProjectPathsAsUtf8Text() {
        final Workspace workspace = Workspace.builder().withId(7L).withName("My workspace").withBasePath("/base").build();
        final MavenProject storedAbsolute = MavenProject.builder().withAbsolutePath("/base/first").withWorkspace(workspace).build();
        final MavenProject storedRelative = MavenProject.builder().withRelativePath("second").withWorkspace(workspace).build();
        when(workspaceService.getWorkspace(7L)).thenReturn(Optional.of(workspace));
        when(workspaceService.getProjectsForWorkspace(7L, true)).thenReturn(List.of(storedAbsolute, storedRelative));

        final ResponseEntity<String> response = workspaceController.exportWorkspacePaths(7L);

        assertEquals("/base/first\n/base/second", response.getBody().replace('\\', '/'));
        assertEquals(StandardCharsets.UTF_8, response.getHeaders().getContentType().getCharset());
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("workspace-paths-My_workspace.txt"));
    }
}
