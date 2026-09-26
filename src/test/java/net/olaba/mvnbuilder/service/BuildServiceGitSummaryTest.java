package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.MavenProject;
import net.olaba.mvnbuilder.model.ActionSummary;
import net.olaba.mvnbuilder.model.CommandResult;
import net.olaba.mvnbuilder.repository.BuildProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildServiceGitSummaryTest {

    @Mock private ProcessExecutionService processExecutionService;
    @Mock private WorkspaceService workspaceService;
    @Mock private BuildProfileRepository buildProfileRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private SystemSettingService systemSettingService;
    @InjectMocks private BuildService buildService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void bulkPullSeparatesFailedRepositoriesFromUpToDateRepositories() {
        final Map<String, CompletableFuture<CommandResult>> results = Map.of(
                "updated", completed(0, "Updating files"),
                "current", completed(0, "Already up to date."),
                "failed", completed(1, "fatal: authentication failed"),
                "exception", CompletableFuture.failedFuture(new IllegalStateException("Git unavailable")));
        when(processExecutionService.executeCommand(anyString(), any(File.class), eq("git"), eq("pull")))
                .thenAnswer(invocation -> results.get(invocation.getArgument(0)));

        buildService.bulkGitPull(List.of(project("updated"), project("current"), project("failed"), project("exception")));

        final ActionSummary summary = sentSummary();
        assertFalse(summary.isSuccess());
        assertEquals(List.of("updated"), summary.getChangedProjects());
        assertEquals(List.of("current"), summary.getNoChangesProjects());
        assertEquals(List.of("failed", "exception"), summary.getFailedProjects());
    }

    @Test
    void bulkFetchReportsFailedRepositories() {
        final Map<String, CompletableFuture<CommandResult>> results = Map.of(
                "ok", completed(0, ""),
                "failed", completed(128, "fatal: repository not found"));
        when(processExecutionService.executeCommand(anyString(), any(File.class), eq("git"), eq("fetch")))
                .thenAnswer(invocation -> results.get(invocation.getArgument(0)));

        buildService.bulkGitFetch(List.of(project("ok"), project("failed")));

        final ActionSummary summary = sentSummary();
        assertFalse(summary.isSuccess());
        assertEquals("git-fetch", summary.getAction());
        assertEquals(List.of("failed"), summary.getFailedProjects());
    }

    private ActionSummary sentSummary() {
        final ArgumentCaptor<ActionSummary> captor = ArgumentCaptor.forClass(ActionSummary.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/action-summary"), captor.capture());
        return captor.getValue();
    }

    private static MavenProject project(final String artifactId) {
        return MavenProject.builder()
                .withArtifactId(artifactId)
                .withAbsolutePath("/tmp/" + artifactId)
                .build();
    }

    private static CompletableFuture<CommandResult> completed(final int exitCode, final String output) {
        return CompletableFuture.completedFuture(new CommandResult(exitCode, List.of(output)));
    }
}
