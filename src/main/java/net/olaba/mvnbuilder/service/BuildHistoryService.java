package net.olaba.mvnbuilder.service;

import lombok.RequiredArgsConstructor;
import net.olaba.mvnbuilder.entities.BuildRun;
import net.olaba.mvnbuilder.entities.BuildRunProject;
import net.olaba.mvnbuilder.entities.BuildRunType;
import net.olaba.mvnbuilder.model.BuildHistoryPage;
import net.olaba.mvnbuilder.model.BuildProjectTiming;
import net.olaba.mvnbuilder.model.BuildProjectView;
import net.olaba.mvnbuilder.model.BuildRunView;
import net.olaba.mvnbuilder.model.ProjectBuildStat;
import net.olaba.mvnbuilder.repository.BuildRunProjectRepository;
import net.olaba.mvnbuilder.repository.BuildRunRepository;
import net.olaba.mvnbuilder.repository.ProjectBuildStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Records build runs with their per-project timings and serves the workspace
 * build history and per-project averages.
 */
@Service
@RequiredArgsConstructor
public class BuildHistoryService {

    /** Number of runs shown per history page. */
    public static final int PAGE_SIZE = 25;

    private final BuildRunRepository buildRunRepository;
    private final BuildRunProjectRepository buildRunProjectRepository;

    /**
     * Starts accumulating a build run.
     *
     * @param workspaceId   owning workspace id, may be null
     * @param workspaceName owning workspace name, may be null
     * @param type          how the run was requested
     * @return a mutable run accumulator
     */
    public BuildRunRecorder start(final Long workspaceId, final String workspaceName, final BuildRunType type) {
        return new BuildRunRecorder(workspaceId, workspaceName, type);
    }

    /**
     * Persists an accumulated run with its per-project timings.
     *
     * @param recorder the run accumulator
     * @param success  whether the run completed successfully
     * @return the persisted run
     */
    @Transactional
    public BuildRun finish(final BuildRunRecorder recorder, final boolean success) {
        final Instant finishedAt = Instant.now();
        final BuildRun run = BuildRun.builder()
                .withWorkspaceId(recorder.workspaceId())
                .withWorkspaceName(recorder.workspaceName())
                .withType(recorder.type())
                .withStartedAt(recorder.startedAt())
                .withFinishedAt(finishedAt)
                .withTotalDurationMs(Math.max(0L, Duration.between(recorder.startedAt(), finishedAt).toMillis()))
                .withSuccess(success)
                .withBuiltProjectCount(recorder.timings().size())
                .build();
        for (final BuildProjectTiming timing : recorder.timings()) {
            run.getProjects().add(BuildRunProject.builder()
                    .withBuildRun(run)
                    .withArtifactId(timing.artifactId())
                    .withDurationMs(timing.durationMs())
                    .withSuccess(timing.success())
                    .withExecutionIndex(timing.executionIndex())
                    .build());
        }
        return buildRunRepository.save(run);
    }

    /**
     * Loads a page of build history plus the all-time per-project averages.
     *
     * @param workspaceId workspace identifier
     * @param page        zero-based page index
     * @return the history page
     */
    @Transactional(readOnly = true)
    public BuildHistoryPage load(final Long workspaceId, final int page) {
        final Page<BuildRun> result = buildRunRepository.findByWorkspaceIdOrderByStartedAtDescIdDesc(
                workspaceId, PageRequest.of(Math.max(0, page), PAGE_SIZE));
        final List<Long> runIds = result.getContent().stream().map(BuildRun::getId).toList();
        final Map<Long, List<BuildRunProject>> timingsByRun = runIds.isEmpty() ? Map.of()
                : buildRunProjectRepository.findByBuildRunIdInOrderByExecutionIndexAsc(runIds).stream()
                        .collect(Collectors.groupingBy(timing -> timing.getBuildRun().getId()));
        final List<BuildRunView> runs = result.getContent().stream()
                .map(run -> new BuildRunView(
                        run.getId(),
                        run.getStartedAt() == null ? null : run.getStartedAt().toString(),
                        run.getType(),
                        run.getBuiltProjectCount(),
                        run.getTotalDurationMs(),
                        run.isSuccess(),
                        timingsByRun.getOrDefault(run.getId(), List.of()).stream()
                                .map(timing -> new BuildProjectView(timing.getArtifactId(), timing.getDurationMs(),
                                        timing.isSuccess(), timing.getExecutionIndex()))
                                .toList()))
                .toList();
        final List<ProjectBuildStat> averages = buildRunProjectRepository.projectAverages(workspaceId).stream()
                .map(stat -> new ProjectBuildStat(stat.getArtifactId(),
                        stat.getAverageMs() == null ? 0.0 : stat.getAverageMs(),
                        stat.getRuns(), stat.getMinMs(), stat.getMaxMs()))
                .toList();
        return new BuildHistoryPage(result.getNumber(), result.getTotalPages(), result.getTotalElements(), runs, averages);
    }

    /**
     * Mutable accumulator for a build run in progress. One instance belongs to a
     * single build execution; timings are added as each project completes.
     */
    public static final class BuildRunRecorder {

        private final Instant startedAt = Instant.now();
        private final Long workspaceId;
        private final String workspaceName;
        private final BuildRunType type;
        private final List<BuildProjectTiming> timings = new ArrayList<>();

        private BuildRunRecorder(final Long workspaceId, final String workspaceName, final BuildRunType type) {
            this.workspaceId = workspaceId;
            this.workspaceName = workspaceName;
            this.type = type;
        }

        /**
         * Records a completed project build.
         *
         * @param artifactId artifact ID of the project
         * @param durationMs measured execution time
         * @param success    whether the project succeeded
         */
        public void projectBuilt(final String artifactId, final long durationMs, final boolean success) {
            timings.add(new BuildProjectTiming(artifactId, Math.max(0L, durationMs), success, timings.size()));
        }

        Instant startedAt() {
            return startedAt;
        }

        Long workspaceId() {
            return workspaceId;
        }

        String workspaceName() {
            return workspaceName;
        }

        BuildRunType type() {
            return type;
        }

        List<BuildProjectTiming> timings() {
            return List.copyOf(timings);
        }
    }
}
