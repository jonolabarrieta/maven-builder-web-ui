package net.olaba.mvnbuilder.model;

import net.olaba.mvnbuilder.entities.BuildRunType;

import java.util.List;

/**
 * A recorded build run prepared for rendering.
 */
public record BuildRunView(
        Long id,
        String startedAt,
        BuildRunType type,
        int projectCount,
        long totalDurationMs,
        boolean success,
        List<BuildProjectView> projects) {

    public BuildRunView {
        projects = projects == null ? List.of() : List.copyOf(projects);
    }

    /** Human-readable total duration. */
    public String getTotalLabel() {
        return DurationFormat.human(totalDurationMs);
    }

    /** Human-readable start timestamp in the local zone. */
    public String getStartedLabel() {
        if (startedAt == null) {
            return "";
        }
        try {
            return java.time.OffsetDateTime.parse(startedAt)
                    .atZoneSameInstant(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (final RuntimeException e) {
            return startedAt;
        }
    }

    /** Human-readable run type. */
    public String getTypeLabel() {
        if (type == null) {
            return "Build";
        }
        return switch (type) {
            case FULL -> "Build All";
            case RANGE -> "Range";
            case PROJECT -> "Single project";
            case SELECTION -> "Selected projects";
        };
    }
}
