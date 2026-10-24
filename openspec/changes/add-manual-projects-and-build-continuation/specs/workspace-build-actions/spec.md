## ADDED Requirements

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
