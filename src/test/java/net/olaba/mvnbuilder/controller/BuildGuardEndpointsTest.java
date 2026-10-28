package net.olaba.mvnbuilder.controller;

import net.olaba.mvnbuilder.entities.*;
import net.olaba.mvnbuilder.model.BuildRangeMode;
import net.olaba.mvnbuilder.repository.*;
import net.olaba.mvnbuilder.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BuildGuardEndpointsTest {
    @Mock WorkspaceService workspaceService;
    @Mock BuildService buildService;
    @Mock TopologicalSortService topologicalSortService;
    @Mock MavenProjectRepository mavenProjectRepository;
    @Mock GitService gitService;
    @Mock MavenRepositoryService mavenRepositoryService;
    @Mock FileSystemService fileSystemService;
    @Mock MavenService mavenService;
    @Mock SystemSettingService systemSettingService;
    @Mock JavaInstallationRepository javaInstallationRepository;
    @Mock UpdateService updateService;
    @Mock DesktopLauncherService desktopLauncherService;
    @Mock WorkspaceGroupService groups;
    @Mock NotificationService notifications;
    @Mock BuildGuardService guard;
    @InjectMocks WorkspaceController controller;
    AutoCloseable mocks;
    MockMvc mvc;
    Workspace workspace = Workspace.builder().withId(1L).withName("ws").build();
    MavenProject api = MavenProject.builder().withId(7L).withArtifactId("api-service").withAbsolutePath("/base/api").withWorkspace(workspace).build();
    List<MavenProject> targets = List.of(api);

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(controller, "buildGuard", guard);
        ReflectionTestUtils.setField(controller, "workspaceGroups", groups);
        WorkspaceGroupController groupController = new WorkspaceGroupController(groups, buildService, gitService, notifications);
        ReflectionTestUtils.setField(groupController, "buildGuard", guard);
        mvc = MockMvcBuilders.standaloneSetup(controller, groupController).setControllerAdvice(new RunGuardAdvice()).build();
        when(workspaceService.getWorkspace(1L)).thenReturn(Optional.of(workspace));
        when(groups.workspace(1L)).thenReturn(workspace);
        when(mavenProjectRepository.findById(7L)).thenReturn(Optional.of(api));
        when(buildService.workspaceTargets(1L)).thenReturn(targets);
        when(buildService.rangeTargets(1L, 7L, BuildRangeMode.FROM)).thenReturn(targets);
        when(groups.scope(eq(1L), any(), any(), anyBoolean())).thenReturn(targets);
        // The mocked guard asks whenever no answer is given, and otherwise starts the build.
        doAnswer(i -> {
            if (i.getArgument(3) == null) throw new BuildGuardService.GuardRequiredException(List.of(new BuildGuardService.RunningEntry(4L, "api")));
            ((Runnable) i.getArgument(4)).run();
            return null;
        }).when(guard).build(any(), any(), any(), any(), any());
    }

    @AfterEach
    void close() throws Exception { mocks.close(); }

    @Test
    void singleProjectBuildAsksThenHonoursTheAnswer() throws Exception {
        mvc.perform(post("/projects/7/build")).andExpect(status().isConflict())
            .andExpect(jsonPath("$.runGuard").value(true))
            .andExpect(jsonPath("$.entries[0].name").value("api"));
        verify(buildService, never()).buildProject(any());

        mvc.perform(post("/projects/7/build").param("runGuard", "IGNORE")).andExpect(status().isOk());
        verify(guard).build(eq(1L), eq("ws"), eq(List.of(api)), eq(BuildGuardService.Mode.IGNORE), any());
        mvc.perform(post("/projects/7/build").param("runGuard", "STOP_RESTART")).andExpect(status().isOk());
        verify(guard).build(eq(1L), eq("ws"), eq(List.of(api)), eq(BuildGuardService.Mode.STOP_RESTART), any());
        verify(buildService, times(2)).buildProject(api);

        mvc.perform(post("/projects/7/build").param("runGuard", "nonsense")).andExpect(status().isBadRequest());
    }

    @Test
    void buildAllAndRangeUseTheirResolvedTargets() throws Exception {
        mvc.perform(post("/workspaces/1/build-all")).andExpect(status().isConflict());
        mvc.perform(post("/workspaces/1/build-all").param("runGuard", "STOP_RESTART")).andExpect(status().isOk());
        verify(buildService).buildWorkspaceSequentially(1L);

        mvc.perform(post("/workspaces/1/projects/7/build-range").param("mode", "FROM")).andExpect(status().isConflict());
        mvc.perform(post("/workspaces/1/projects/7/build-range").param("mode", "FROM").param("runGuard", "IGNORE")).andExpect(status().isAccepted());
        verify(buildService).buildWorkspaceRange(1L, 7L, BuildRangeMode.FROM);
        verify(guard, atLeastOnce()).build(eq(1L), any(), eq(targets), any(), any());
    }

    @Test
    void selectionAndGroupBuildsAreGuarded() throws Exception {
        mvc.perform(post("/workspaces/1/bulk-action").param("projectIds", "7").param("action", "build")).andExpect(status().isConflict());
        mvc.perform(post("/workspaces/1/bulk-action").param("projectIds", "7").param("action", "build").param("runGuard", "IGNORE")).andExpect(status().isOk());
        verify(buildService).buildProjectsSequentially(targets);

        mvc.perform(post("/workspaces/1/groups/3/action").param("action", "build")).andExpect(status().isConflict());
        mvc.perform(post("/workspaces/1/groups/3/action").param("action", "build").param("runGuard", "STOP_RESTART")).andExpect(status().isOk());
        verify(buildService, times(2)).buildProjectsSequentially(targets);
    }

    @Test
    void gitBulkActionsAreNotGuarded() throws Exception {
        mvc.perform(post("/workspaces/1/bulk-action").param("projectIds", "7").param("action", "fetch")).andExpect(status().isOk());
        verify(guard, never()).build(any(), any(), any(), any(), any());
    }
}
