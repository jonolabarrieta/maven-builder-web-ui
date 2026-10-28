package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.*;
import net.olaba.mvnbuilder.model.LocalNotificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BuildGuardServiceTest {
    RunConfigurationService configurations = mock(RunConfigurationService.class);
    RunProcessRegistry processes = mock(RunProcessRegistry.class);
    BuildService builds = mock(BuildService.class);
    NotificationService notifications = mock(NotificationService.class);
    SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    BuildGuardService guard = new BuildGuardService(configurations, processes, builds, notifications, messaging);
    Workspace workspace = Workspace.builder().withId(1L).withName("ws").build();
    MavenProject api = MavenProject.builder().withId(7L).withArtifactId("api-service").withAbsolutePath("/base/api").withWorkspace(workspace).build();
    MavenProject lib = MavenProject.builder().withId(8L).withArtifactId("lib").withAbsolutePath("/base/lib").withWorkspace(workspace).build();
    RunConfigurationService.LaunchSpec apiSpec = new RunConfigurationService.LaunchSpec(1L, 4L, "api", "/base/api", "run", Map.of(), null);
    AtomicReference<String> token = new AtomicReference<>();

    static RunEntry entry(long id, String name, Long projectId) {
        RunEntry e = new RunEntry(); e.setId(id); e.setName(name); e.setProjectId(projectId); return e;
    }

    @BeforeEach
    void setup() {
        when(processes.runningEntries(1L)).thenReturn(Set.of(4L));
        when(configurations.list(1L)).thenReturn(List.of(entry(4L, "api", 7L), entry(5L, "infra", null)));
        when(configurations.projectOptions(1L)).thenReturn(List.of(
            new RunConfigurationService.ProjectOption(7L, "api-service", "/base/api", false),
            new RunConfigurationService.ProjectOption(8L, "lib", "/base/lib", false)));
        when(configurations.launchSpec(1L, 4L)).thenReturn(apiSpec);
        doAnswer(i -> { token.set(i.getArgument(0)); ((Runnable) i.getArgument(1)).run(); return null; })
            .when(builds).startGuarded(anyString(), any());
    }

    @Test
    void noRunningEntryInvolvedStartsWithoutAsking() {
        Runnable start = mock(Runnable.class);
        guard.build(1L, "ws", List.of(lib), null, start);
        verify(start).run();
        verify(processes, never()).stop(any(), any());
    }

    @Test
    void runningEntryRequiresADecision() {
        Runnable start = mock(Runnable.class);
        var required = assertThrows(BuildGuardService.GuardRequiredException.class, () -> guard.build(1L, "ws", List.of(lib, api), null, start));
        assertEquals(List.of(new BuildGuardService.RunningEntry(4L, "api")), required.getEntries());
        verify(start, never()).run();
    }

    @Test
    void disabledOrFolderTargetsDoNotTriggerTheGuard() {
        api.setEnabled(false);
        assertTrue(guard.conflicts(1L, List.of(api)).isEmpty());
    }

    @Test
    void directoryMatchCoversAnotherRegistrationOfTheSameProject() {
        MavenProject sameDir = MavenProject.builder().withId(70L).withArtifactId("api-service").withAbsolutePath("/base/api/").withWorkspace(workspace).build();
        assertEquals(1, guard.conflicts(1L, List.of(sameDir)).size());
    }

    @Test
    void buildAnywayLeavesTheEntryRunning() {
        Runnable start = mock(Runnable.class);
        guard.build(1L, "ws", List.of(api), BuildGuardService.Mode.IGNORE, start);
        verify(start).run();
        verify(processes, never()).stop(any(), any());
    }

    @Test
    void stopBuildAndRestartAfterSuccess() {
        Runnable start = mock(Runnable.class);
        guard.build(1L, "ws", List.of(api), BuildGuardService.Mode.STOP_RESTART, start);
        var order = inOrder(processes, start);
        order.verify(processes).stop(1L, 4L);
        order.verify(start).run();
        verify(processes, never()).start(any());

        guard.onBuildFinished(new BuildService.BuildFinishedEvent(1L, "run", true, "other-token"));
        verify(processes, never()).start(any());
        guard.onBuildFinished(new BuildService.BuildFinishedEvent(1L, "run", true, token.get()));
        verify(processes).start(apiSpec);
        // A second event for the same token does nothing.
        guard.onBuildFinished(new BuildService.BuildFinishedEvent(1L, "run", true, token.get()));
        verify(processes, times(1)).start(any());
    }

    @Test
    void failedBuildLeavesEntriesStoppedAndSaysSo() {
        guard.build(1L, "ws", List.of(api), BuildGuardService.Mode.STOP_RESTART, () -> { });
        guard.onBuildFinished(new BuildService.BuildFinishedEvent(1L, "run", false, token.get()));
        verify(processes, never()).start(any());
        ArgumentCaptor<LocalNotificationRequest> notice = ArgumentCaptor.forClass(LocalNotificationRequest.class);
        verify(notifications).recordLocal(notice.capture());
        assertTrue(notice.getValue().message().contains("api"));
        assertTrue(notice.getValue().message().contains("not restarted"));
        verify(processes).note(eq(1L), eq(4L), contains("Not restarted"));
    }

    @Test
    void buildThatCannotStartRestoresTheEntries() {
        assertThrows(IllegalArgumentException.class, () -> guard.build(1L, "ws", List.of(api), BuildGuardService.Mode.STOP_RESTART,
            () -> { throw new IllegalArgumentException("bad range"); }));
        verify(processes).stop(1L, 4L);
        verify(processes).start(apiSpec);
    }
}
