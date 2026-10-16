package net.olaba.mvnbuilder.model;

import net.olaba.mvnbuilder.entities.NotificationCategory;

import java.util.List;

/**
 * Response payload describing a persisted notification for the history panel.
 */
public record NotificationDto(
        Long id,
        String createdAt,
        NotificationCategory category,
        String action,
        boolean success,
        String title,
        String message,
        Long workspaceId,
        String workspaceName,
        List<String> failedProjects,
        List<String> succeededProjects,
        List<String> changedProjects,
        List<String> noChangesProjects,
        boolean read) {

    public NotificationDto {
        failedProjects = failedProjects == null ? List.of() : List.copyOf(failedProjects);
        succeededProjects = succeededProjects == null ? List.of() : List.copyOf(succeededProjects);
        changedProjects = changedProjects == null ? List.of() : List.copyOf(changedProjects);
        noChangesProjects = noChangesProjects == null ? List.of() : List.copyOf(noChangesProjects);
    }
}
