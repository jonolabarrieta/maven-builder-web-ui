package net.olaba.mvnbuilder.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.model.WorkspaceProjectHierarchy;
import net.olaba.mvnbuilder.repository.JavaInstallationRepository;
import net.olaba.mvnbuilder.repository.MavenProjectRepository;
import net.olaba.mvnbuilder.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Contract checks for targeted Git results and both Refresh response modes. */
class WorkspaceControllerRefreshTest {

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
    @InjectMocks private WorkspaceController controller;

    /** Initializes the controller with isolated collaborators. */
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    /** Branch updates contain only addressed data, including unknown branches. */
    @Test
    void gitResponseContainsOnlyTargetedBranchData() throws Exception {
        final MavenProject first = MavenProject.builder().withId(7L)
                .withGitBranch("topic/\"<script>").build();
        final MavenProject unknown = MavenProject.builder().withId(8L).build();
        when(workspaceService.refreshGitStatus(1L)).thenReturn(List.of(first, unknown));

        final List<WorkspaceController.GitBranchUpdate> updates = controller.getGitStatusTable(1L);
        final String json = new ObjectMapper().writeValueAsString(updates);

        assertEquals(2, updates.size());
        assertEquals("topic/\"<script>", updates.get(0).branch());
        assertNull(updates.get(1).branch());
        assertTrue(json.contains("\\\"<script>"));
        assertFalse(json.contains("<tr"));
        assertFalse(json.contains("checkbox"));
    }

    /** A failed Git lookup propagates as a failed request to the client coordinator. */
    @Test
    void gitFailureDoesNotReturnAReplacementTable() {
        when(workspaceService.refreshGitStatus(1L)).thenThrow(new IllegalStateException("git unavailable"));
        assertThrows(IllegalStateException.class, () -> controller.getGitStatusTable(1L));
    }

    /** HTMX receives the updated project fragment and the child indicator set. */
    @Test
    void htmxRefreshReturnsTableBody() {
        final Workspace workspace = Workspace.builder().withId(1L).build();
        final MavenProject parent = MavenProject.builder().withId(4L).build();
        when(workspaceService.getWorkspace(1L)).thenReturn(Optional.of(workspace));
        when(workspaceService.getProjectHierarchyForWorkspace(1L))
                .thenReturn(new WorkspaceProjectHierarchy(List.of(parent), Set.of(4L)));
        final ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("workspace-detail :: project-table-body", controller.refreshWorkspace(1L, true, model));
        verify(workspaceService).refreshWorkspace(1L);
        assertEquals(workspace, model.get("workspace"));
        assertEquals(List.of(parent), model.get("projects"));
        assertEquals(Set.of(4L), model.get("projectIdsWithChildren"));
    }

    /** A normal form still gets the original POST/redirect behavior. */
    @Test
    void normalRefreshRedirects() {
        final ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("redirect:/workspaces/1", controller.refreshWorkspace(1L, false, model));
        verify(workspaceService).refreshWorkspace(1L);
        verify(workspaceService, never()).getProjectHierarchyForWorkspace(1L);
    }

    /** Initial rendering uses one hierarchy snapshot for rows and indicators. */
    @Test
    void detailViewUsesOneHierarchySnapshot() {
        final Workspace workspace = Workspace.builder().withId(1L).build();
        final MavenProject parent = MavenProject.builder().withId(4L).build();
        when(workspaceService.getWorkspace(1L)).thenReturn(Optional.of(workspace));
        when(workspaceService.getProjectHierarchyForWorkspace(1L))
                .thenReturn(new WorkspaceProjectHierarchy(List.of(parent), Set.of(4L)));
        when(javaInstallationRepository.findAll()).thenReturn(List.of());
        final ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("workspace-detail", controller.viewWorkspace(1L, model));
        assertEquals(List.of(parent), model.get("projects"));
        assertEquals(Set.of(4L), model.get("projectIdsWithChildren"));
        verify(workspaceService, times(1)).getProjectHierarchyForWorkspace(1L);
    }
}
