## MODIFIED Requirements

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

## ADDED Requirements

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
