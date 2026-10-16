package net.olaba.mvnbuilder.service;

import net.olaba.mvnbuilder.entities.BuildRun;
import net.olaba.mvnbuilder.entities.BuildRunType;
import net.olaba.mvnbuilder.model.BuildHistoryPage;
import net.olaba.mvnbuilder.model.BuildRunView;
import net.olaba.mvnbuilder.model.ProjectBuildStat;
import net.olaba.mvnbuilder.repository.BuildRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies recording, retention ordering, averages and pagination of build history. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:build-history-tests;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(BuildHistoryService.class)
class BuildHistoryServiceTest {

    @Autowired private BuildHistoryService service;
    @Autowired private BuildRunRepository runRepository;

    /** A full run persists its per-project timings in execution order. */
    @Test
    void recordsFullRunWithOrderedTimings() {
        final BuildHistoryService.BuildRunRecorder recorder = service.start(5L, "Platform", BuildRunType.FULL);
        recorder.projectBuilt("mod-a", 1000, true);
        recorder.projectBuilt("mod-b", 2500, true);

        final BuildRun saved = service.finish(recorder, true);

        assertNotNull(saved.getId());
        assertEquals(5L, saved.getWorkspaceId());
        assertEquals("Platform", saved.getWorkspaceName());
        assertEquals(BuildRunType.FULL, saved.getType());
        assertEquals(2, saved.getBuiltProjectCount());
        assertTrue(saved.isSuccess());
        assertEquals(List.of("mod-a", "mod-b"),
                saved.getProjects().stream().map(p -> p.getArtifactId()).toList());
        assertEquals(List.of(0, 1),
                saved.getProjects().stream().map(p -> p.getExecutionIndex()).toList());
        assertEquals(2500, saved.getProjects().get(1).getDurationMs());
    }

    /** A failed run keeps the projects that ran and marks the run unsuccessful. */
    @Test
    void recordsPartialTimingsOnFailure() {
        final BuildHistoryService.BuildRunRecorder recorder = service.start(5L, "Platform", BuildRunType.SELECTION);
        recorder.projectBuilt("mod-a", 800, true);
        recorder.projectBuilt("mod-b", 300, false);

        final BuildRun saved = service.finish(recorder, false);

        assertFalse(saved.isSuccess());
        assertEquals(2, saved.getBuiltProjectCount());
        assertFalse(saved.getProjects().get(1).isSuccess());
    }

    /** Per-project averages aggregate every recorded run. */
    @Test
    void computesPerProjectAverages() {
        record(5L, BuildRunType.FULL, "mod-a", 1000, true);
        record(5L, BuildRunType.RANGE, "mod-a", 3000, true);
        record(5L, BuildRunType.FULL, "mod-b", 500, true);

        final BuildHistoryPage page = service.load(5L, 0);

        final ProjectBuildStat statsA = page.averages().stream()
                .filter(stat -> "mod-a".equals(stat.artifactId())).findFirst().orElseThrow();
        assertEquals(2000.0, statsA.averageMs(), 0.001);
        assertEquals(2, statsA.runs());
        assertEquals(1000, statsA.minMs());
        assertEquals(3000, statsA.maxMs());
        assertTrue(page.averages().stream().anyMatch(stat -> "mod-b".equals(stat.artifactId())));
    }

    /** The run list is paginated newest first. */
    @Test
    void paginatesRunsNewestFirst() {
        for (int index = 0; index < BuildHistoryService.PAGE_SIZE + 1; index++) {
            record(5L, BuildRunType.FULL, "mod-" + index, 100 + index, true);
        }

        final BuildHistoryPage first = service.load(5L, 0);
        assertEquals(BuildHistoryService.PAGE_SIZE, first.runs().size());
        assertEquals(2, first.totalPages());
        assertEquals(BuildHistoryService.PAGE_SIZE + 1L, first.totalRuns());
        assertEquals("mod-" + BuildHistoryService.PAGE_SIZE, first.runs().get(0).projects().get(0).artifactId());

        final BuildHistoryPage second = service.load(5L, 1);
        assertEquals(1, second.runs().size());
    }

    /** An unknown workspace returns an empty history. */
    @Test
    void emptyWorkspaceReturnsNoRuns() {
        final BuildHistoryPage page = service.load(99L, 0);
        assertTrue(page.runs().isEmpty());
        assertTrue(page.averages().isEmpty());
        assertEquals(0, page.totalRuns());
    }

    private void record(final Long workspaceId, final BuildRunType type, final String artifactId,
            final long durationMs, final boolean success) {
        final BuildHistoryService.BuildRunRecorder recorder = service.start(workspaceId, "Platform", type);
        recorder.projectBuilt(artifactId, durationMs, success);
        service.finish(recorder, success);
    }
}
