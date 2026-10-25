## ADDED Requirements

### Requirement: Group action parity and individual access
The system SHALL expose build, Git fetch, pull, checkout or branch creation, stash, discard, unstage, refresh, and enable or disable actions for local and imported groups. It SHALL retain the existing per-project eligibility rules, options, confirmations, progress and outcome feedback. Inapplicable actions SHALL be disabled or explained. Individual members SHALL retain their applicable actions, including local shortcuts and Maven metadata; a group SHALL NOT require a fabricated directory or POM.

#### Scenario: Pull an imported group
- **WHEN** the user requests Git pull for imported group G
- **THEN** the system SHALL operate on eligible repositories resolved from G's member paths and report per-repository results under the consuming workspace.

#### Scenario: Act on one member
- **WHEN** the user fetches project X inside G
- **THEN** the action SHALL target X's repository and SHALL NOT expand into all repositories in G.

#### Scenario: Confirm a destructive action
- **WHEN** the user requests discard for G
- **THEN** the existing confirmation flow SHALL identify G and its affected repositories before execution.

### Requirement: Server-resolved action scope
The system SHALL validate the consumer, group ownership, current reference, and every requested member before starting a group or contextual individual action. Membership through a valid imported parent SHALL grant access through that group while preserving source ownership. An invalid member SHALL reject the entire request without partial execution. Empty eligible scopes SHALL report that there is nothing to execute.

#### Scenario: Reject a foreign project
- **WHEN** a group action for A/G includes project X outside G
- **THEN** the request SHALL be rejected and no project action SHALL start.

#### Scenario: Reject a removed reference
- **WHEN** a request targets a previously imported group that has been removed
- **THEN** it SHALL be rejected even if its source projects still exist in the parent.

### Requirement: Deduplicate Git repository targets
Within one group, workspace, or mixed-selection action the system SHALL execute each Git operation at most once per canonical repository root. Multiple selected project rows sharing a root SHALL map to the same outcome. Worktree repositories SHALL be resolved correctly. Feedback SHALL identify shared repositories whose working-tree changes can be observed from other rows or consumers.

#### Scenario: Multiple modules share a repository
- **WHEN** selected projects X and Y in one action resolve to the same repository
- **THEN** fetch, pull, checkout, stash, discard or unstage SHALL run once for that root and both rows SHALL reflect the resulting state.

#### Scenario: Selected members have distinct repositories
- **WHEN** an action selects X and Y in different repositories and excludes Z
- **THEN** only X and Y's repositories SHALL be targeted and Z's distinct repository SHALL remain outside the plan.
