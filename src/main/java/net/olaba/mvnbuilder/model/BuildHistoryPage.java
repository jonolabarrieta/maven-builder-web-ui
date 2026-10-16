package net.olaba.mvnbuilder.model;

import java.util.List;

/**
 * A page of build history plus the all-time per-project averages for a workspace.
 */
public record BuildHistoryPage(
        int page,
        int totalPages,
        long totalRuns,
        List<BuildRunView> runs,
        List<ProjectBuildStat> averages) {

    public BuildHistoryPage {
        runs = runs == null ? List.of() : List.copyOf(runs);
        averages = averages == null ? List.of() : List.copyOf(averages);
    }
}
