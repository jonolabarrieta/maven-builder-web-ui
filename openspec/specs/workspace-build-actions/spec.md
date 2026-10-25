# workspace-build-actions Specification

## Purpose
Provide downloadable transient build output and inclusive module-level build ranges from the workspace page.

## Requirements

### Requirement: Download Current Build Log
The system SHALL let the user download the build output currently captured in the workspace page as a UTF-8 plain-text file without persisting it server-side. The file SHALL preserve all captured tab labels and lines, not only the tab currently visible.

#### Scenario: User downloads the current build session
- **WHEN** the user selects the build-log download action after one or more log lines have been received
- **THEN** the browser downloads a `.txt` file containing a workspace/timestamp header and all captured log tabs and lines in their displayed order.

#### Scenario: No build output is available
- **WHEN** the user selects the build-log download action before any log line has been captured
- **THEN** the system does not create an empty misleading download and informs the user that there is no log output to download.

### Requirement: Build an Inclusive Range from a Module
The system SHALL provide a module-level build selector with actions to build only the selected module, from it through the last project, or from the first project through it. The range SHALL include the selected module and follow effective execution order. Actions invoked inside a group SHALL use that group's boundary; actions on ungrouped projects SHALL use the workspace composition. ONLY SHALL execute one project using the active profile and consuming workspace Java configuration. Existing disabled-project and failure behavior SHALL be retained.

#### Scenario: Build from the selected module
- **WHEN** the user selects Build from here for an ungrouped module
- **THEN** the system SHALL sequentially build it and later projects in effective workspace order, subject to eligibility and existing failure behavior.

#### Scenario: Build through the selected module
- **WHEN** the user selects Build through here for an ungrouped module
- **THEN** the system SHALL sequentially build projects from the start of the effective workspace order through that module.

#### Scenario: Build only the selected module
- **WHEN** the user selects Only this module for a module
- **THEN** only that module SHALL be built using the active profile and consuming workspace Java configuration.

#### Scenario: Project does not belong to the workspace
- **WHEN** a range-build request names a project outside the workspace's own or valid imported membership
- **THEN** the system SHALL reject the request and SHALL NOT start any build.

#### Scenario: Bound a range to its group
- **WHEN** G contains A, B, C and another group contains D and the user chooses Build from here for B in G
- **THEN** the range SHALL contain B and C and SHALL NOT include D.

### Requirement: Retry or skip a stopped build
After sequential workspace, range, or selection builds stop on a project failure, the system SHALL offer Retry from here and Skip and continue for that failed run. Retry SHALL include the failed project and its original pending tail; skip SHALL omit exactly that failed project and continue its pending tail. Completed projects SHALL not be rebuilt. Skipping SHALL not change persistent enabled state, recompute the queue from current selection/order, or automatically skip dependencies or later failures.

#### Scenario: Retry the failed project
- **WHEN** the original queue is A, B, C, D, A succeeds, B fails, and the user selects Retry from here
- **THEN** the resumed queue SHALL be B, C, D in that order.

#### Scenario: Skip the failed project
- **WHEN** the original queue is A, B, C, D, A succeeds, B fails, and the user selects Skip and continue
- **THEN** only C and D SHALL be resumed in that order and B SHALL retain its persistent enabled state.

#### Scenario: No pending projects remain
- **WHEN** the user skips the last failed project and no eligible pending projects remain
- **THEN** the view SHALL report No remaining projects, start no empty build, and retain the failed run outcome.

### Requirement: Scoped validated build continuations
Continuation requests SHALL reference a server-owned queue with workspace, run, failure, and captured build-command/Java settings. The system SHALL validate workspace membership and current target availability/kind, retain disabled-project skip rules, and reject invalid or expired queues with guidance. Failure UI SHALL display only matching-workspace events. Acceptance SHALL be atomic and duplicate submissions SHALL not launch another continuation. Cancellation SHALL invalidate that run's pending continuation; restarting the application or expiry SHALL require a fresh build.

#### Scenario: Selection or order changes after failure
- **WHEN** the workspace selection, execution order, or active profile changes after a failure
- **THEN** continuation SHALL retain the original pending order and captured build/Java settings rather than using a newly assembled workspace queue.

#### Scenario: Cross-workspace or changed target
- **WHEN** a continuation refers to another workspace, or an original pending target was removed, reclassified, or became unavailable
- **THEN** the server SHALL reject it without starting an altered queue and SHALL provide review/restart guidance.

#### Scenario: Duplicate submission or old action
- **WHEN** retry and skip are submitted for the same continuation or a consumed, canceled, or expired continuation is submitted again
- **THEN** at most one accepted request SHALL start execution and other requests SHALL receive explicit rejection.

#### Scenario: Unrelated failure event
- **WHEN** a build failure for another workspace is broadcast
- **THEN** the current workspace's recovery controls SHALL not be replaced by that failure.

### Requirement: Accurate continuation feedback and history
The system SHALL retain the original failed build history and record subsequent executed processes in a separate run. An explicitly skipped failed project SHALL be named in continuation logs, summaries, and notifications, with no successful timing attributed to it. A successfully executed pending tail SHALL not imply that the skipped project or original run succeeded. Another failure SHALL offer a new continuation for its remaining tail.

#### Scenario: Remaining projects succeed after a skip
- **WHEN** B failed and the user skips it before C and D succeed
- **THEN** feedback SHALL state that B was skipped and the pending builds completed, while the original run remains failed and B has no fabricated successful duration.

#### Scenario: A later project fails
- **WHEN** a resumed tail fails at C
- **THEN** C SHALL become the failure of a new scoped continuation for C and its remaining tail without rebuilding completed earlier projects or re-enabling the consumed action.

### Requirement: Effective grouped build plan
Build All SHALL resolve the current ordered workspace composition, including imported groups. Group build SHALL resolve only that group. Plans SHALL execute available enabled Maven targets sequentially, omit explicit non-Maven entries with existing feedback, deduplicate canonical project directories retaining first occurrence, and retain the source project ownership. Active profile, Java configuration, logs, history and notifications SHALL use explicit consumer context rather than infer it from an imported project's owner. No group action SHALL silently add targets outside its scope.

#### Scenario: Build a shared group in a consumer
- **WHEN** A builds imported group P using Java configured in A
- **THEN** its eligible Maven members SHALL execute in P's order with A's Java context and results SHALL be recorded for A while P remains their owner.

#### Scenario: Shared directory appears twice
- **WHEN** a workspace build resolves the same canonical project directory through two registrations
- **THEN** it SHALL be executed once at its first effective position.

#### Scenario: No eligible Maven targets
- **WHEN** a group contains only non-Maven, disabled or unavailable targets
- **THEN** no Maven process SHALL start and feedback SHALL explain why there are no eligible targets.

### Requirement: Group-scoped build continuation
Retry and skip for grouped builds SHALL preserve the original resolved pending queue, command, Java configuration and consumer/group context. Completed projects SHALL NOT be rebuilt and skip SHALL NOT change enabled state. Later order or selection changes SHALL NOT recompute the pending queue. Continuations SHALL retain existing single-use and stale validation and SHALL reject removed groups, removed references or inaccessible pending members without executing a partial replacement queue. Existing failure and continuation history semantics SHALL be retained.

#### Scenario: Reorder after a failure
- **WHEN** G's original queue is A, B, C, A succeeds, B fails, and G is later reordered
- **THEN** retry SHALL execute the captured B, C queue and skip SHALL execute captured C without rebuilding A or using the new order.

#### Scenario: Remove the import before retry
- **WHEN** the imported group for a failed build is removed before continuation
- **THEN** retry or skip SHALL be rejected with feedback and no remaining process SHALL start.
