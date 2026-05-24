package net.olaba.mvnbuilder.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Payload sent to the UI via WebSockets after executing an action to summarize the outcome.
 */
@Getter
@Setter
@Builder(setterPrefix = "with")
@NoArgsConstructor
@AllArgsConstructor
public class ActionSummary {
    /** The action name ("build" or "git-pull"). */
    private String action;
    
    /** Whether the action was fully successful. */
    private boolean success;
    
    /** The artifact ID of the project that failed (if any). */
    private String failedProject;
    
    /** The list of artifact IDs of projects that completed successfully. */
    private List<String> succeededProjects;
    
    /** For git-pull: repositories that were already up-to-date. */
    private List<String> noChangesProjects;
    
    /** For git-pull: repositories that pulled new changes. */
    private List<String> changedProjects;
}
