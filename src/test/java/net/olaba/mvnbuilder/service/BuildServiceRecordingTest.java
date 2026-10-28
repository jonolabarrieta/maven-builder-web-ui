package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.BuildRun;
import net.olaba.mvnbuilder.entities.BuildRunProject;
import net.olaba.mvnbuilder.entities.BuildRunType;
import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.entities.Workspace;
import net.olaba.mvnbuilder.model.BuildRangeMode;
import net.olaba.mvnbuilder.model.CommandResult;
import net.olaba.mvnbuilder.repository.BuildRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Verifies that BuildService records runs of the right type and content. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:build-recording-tests;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({BuildService.class, BuildHistoryService.class})
@org.springframework.test.context.event.RecordApplicationEvents
class BuildServiceRecordingTest {

    @Autowired private org.springframework.test.context.event.ApplicationEvents events;

    @Autowired private BuildService buildService;
    @Autowired private BuildRunRepository runRepository;
    @MockBean private ProcessExecutionService processExecutionService;
    @MockBean private WorkspaceService workspaceService;
    @MockBean private SimpMessagingTemplate messagingTemplate;
    @MockBean private SystemSettingService systemSettingService;
    @MockBean private NotificationService notificationService;

    private final AtomicReference<String> failingLabel = new AtomicReference<>(null);

    @BeforeEach
    void setUp() {
        final Workspace workspace = Workspace.builder().withId(1L).withName("Platform").withBasePath("/tmp").build();
        when(workspaceService.getProjectsForWorkspace(1L, true))
                .thenReturn(List.of(project(10L, "mod-a", workspace, true), project(11L, "mod-b", workspace, true),
                        project(12L, "mod-c", workspace, false)));
        when(processExecutionService.executeCommandWithJavaHome(anyString(), any(), any(), any(String[].class)))
                .thenAnswer(invocation -> {
                    final String label = invocation.getArgument(0);
                    final int exitCode = label.equals(failingLabel.get()) ? 1 : 0;
                    return CompletableFuture.completedFuture(new CommandResult(exitCode, List.of("ok"), 1200));
                });
    }

    /** Build All records a FULL run with only the enabled projects. */
    @Test
    void buildAllRecordsFullRunExcludingDisabled() {
        buildService.buildWorkspaceSequentially(1L);

        final List<BuildRun> runs = runRepository.findAll();
        assertEquals(1, runs.size());
        final BuildRun run = runs.get(0);
        assertEquals(BuildRunType.FULL, run.getType());
        assertEquals("Platform", run.getWorkspaceName());
        assertEquals(2, run.getBuiltProjectCount());
        assertTrue(run.isSuccess());
        assertEquals(List.of("mod-a", "mod-b"), run.getProjects().stream()
                .map(BuildRunProject::getArtifactId).toList());
        assertEquals(1200, run.getProjects().get(0).getDurationMs());
        verify(processExecutionService, never())
                .executeCommandWithJavaHome(eq("mod-c"), any(), any(), any(String[].class));
    }

    /** A failing project stops the run and is recorded as unsuccessful. */
    @Test
    void failureStopsRunAndRecordsPartialTimings() {
        failingLabel.set("mod-b");

        buildService.buildWorkspaceSequentially(1L);

        final BuildRun run = runRepository.findAll().get(0);
        assertFalse(run.isSuccess());
        assertEquals(2, run.getBuiltProjectCount());
        assertEquals(List.of("mod-a", "mod-b"), run.getProjects().stream()
                .map(BuildRunProject::getArtifactId).toList());
        assertTrue(run.getProjects().get(0).isSuccess());
        assertFalse(run.getProjects().get(1).isSuccess());
    }

    /** A single-project build records a PROJECT run. */
    @Test
    void singleProjectRecordsProjectRun() {
        buildService.buildProject(project(10L, "mod-a", workspace(), true));

        final BuildRun run = runRepository.findAll().get(0);
        assertEquals(BuildRunType.PROJECT, run.getType());
        assertEquals(1, run.getBuiltProjectCount());
        assertEquals("mod-a", run.getProjects().get(0).getArtifactId());
    }

    /** A range build records a RANGE run. */
    @Test
    void rangeBuildRecordsRangeRun() {
        buildService.buildWorkspaceRange(1L, 11L, BuildRangeMode.THROUGH);

        final BuildRun run = runRepository.findAll().get(0);
        assertEquals(BuildRunType.RANGE, run.getType());
        assertEquals(2, run.getBuiltProjectCount());
    }

    private List<BuildService.BuildFinishedEvent> finished() {
        return events.stream(BuildService.BuildFinishedEvent.class).toList();
    }

    /** Successful, failed, single-project and guarded builds each publish one finished event. */
    @Test
    void finishedEventsCarryOutcomeAndGuardToken() {
        buildService.buildWorkspaceSequentially(1L);
        assertEquals(1, finished().size());
        assertTrue(finished().get(0).success());
        assertEquals(1L, finished().get(0).workspaceId());
        assertNull(finished().get(0).guardToken());

        failingLabel.set("mod-b");
        buildService.startGuarded("token-1", () -> buildService.buildWorkspaceSequentially(1L));
        assertEquals(2, finished().size());
        assertFalse(finished().get(1).success());
        assertEquals("token-1", finished().get(1).guardToken());

        failingLabel.set(null);
        buildService.startGuarded("token-2", () -> buildService.buildProject(project(10L, "mod-a", workspace(), true)));
        assertEquals("token-2", finished().get(2).guardToken());
        assertTrue(finished().get(2).success());

        // The token is not leaked to later, unguarded builds.
        buildService.buildWorkspaceRange(1L, 11L, BuildRangeMode.THROUGH);
        assertNull(finished().get(3).guardToken());
    }

    /** A canceled run reports an unsuccessful finish. */
    @Test
    void canceledBuildPublishesUnsuccessfulEvent() {
        when(processExecutionService.executeCommandWithJavaHome(anyString(), any(), any(), any(String[].class)))
                .thenAnswer(invocation -> {
                    buildService.cancelContinuations();
                    return CompletableFuture.completedFuture(new CommandResult(0, List.of("ok"), 10));
                });
        buildService.buildWorkspaceSequentially(1L);
        assertEquals(1, finished().size());
        assertFalse(finished().get(0).success());
    }

    private Workspace workspace() {
        return Workspace.builder().withId(1L).withName("Platform").withBasePath("/tmp").build();
    }

    private static MavenProject project(final Long id, final String artifactId, final Workspace workspace,
            final boolean enabled) {
        return MavenProject.builder().withId(id).withArtifactId(artifactId)
                .withAbsolutePath("/tmp/" + artifactId).withWorkspace(workspace).withEnabled(enabled).build();
    }
}
