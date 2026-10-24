## ADDED Requirements

### Requirement: Workspace-wide Git repository discovery
The system SHALL discover every supported Git working-tree root at or beneath the workspace base folder independently of Maven registration, Maven scan exclusions, project selection, and skipped-project state. Discovery SHALL run on entry to the Git view and through an explicit Refresh repositories action, without fetching remotes.

#### Scenario: Non-Maven and skipped projects are included
- **WHEN** the base folder contains a clean non-Maven repository, a modified Maven repository, and a repository whose registered projects are all skipped
- **THEN** all three repositories SHALL appear in the Git view without creating or changing Maven project registrations.

#### Scenario: Base folder is a repository
- **WHEN** the configured base folder is itself a Git working-tree root
- **THEN** discovery SHALL include that repository and continue scanning its subdirectories for other repositories.

### Requirement: Repository identity and nested working trees
The system SHALL recognize `.git` folders and files, validate candidates using Git, and deduplicate canonical working-tree roots. Nested repositories and initialized Git submodules SHALL appear independently; linked worktrees SHALL remain distinct even when they share Git metadata. Multiple Maven modules in one working tree SHALL map to one repository entry.

#### Scenario: Modules share a repository
- **WHEN** three Maven modules belong to one working tree
- **THEN** discovery SHALL expose one repository entry rather than three independently actionable duplicates.

#### Scenario: Nested repository, submodule, and linked worktree
- **WHEN** a repository contains a nested repository and an initialized submodule, and another linked worktree exists beneath the base folder
- **THEN** each distinct working-tree root SHALL have its own stable workspace-scoped identity and display path.

### Requirement: Bounded discovery with explicit outcomes
Discovery SHALL avoid following directory symlinks or entering Git administrative directories, SHALL not stop at a repository root, and SHALL report unreadable/unsupported candidates or resource-limit interruptions. Long scans SHALL expose progress without blocking the rest of the page. Incomplete results SHALL be labelled as incomplete rather than represented as a complete scan.

#### Scenario: Unreadable directory during a long scan
- **WHEN** a scan discovers repositories but cannot inspect another subtree
- **THEN** discovered repositories SHALL remain usable and the view SHALL show the unreadable path warning and scan completion state.

#### Scenario: Missing base folder or no repositories
- **WHEN** the base folder is missing, invalid, or contains no repositories
- **THEN** the view SHALL distinguish configuration/access failure from a successful empty scan and offer an appropriate settings or refresh action.

### Requirement: Workspace-scoped repository access
Every repository/status/diff/mutation request SHALL resolve its repository identity server-side and validate current membership beneath the canonical workspace base folder. The system SHALL reject stale identities, cross-workspace targets, administrative paths, and traversal requests. Legitimate linked-worktree metadata outside the base folder SHALL remain supported, without allowing the client to select arbitrary external working trees.

#### Scenario: Repository is removed or moved outside the workspace
- **WHEN** a request references a previously discovered root that no longer belongs to the workspace
- **THEN** the request SHALL fail without executing its Git action and the view SHALL request discovery refresh.

#### Scenario: Base folder is inside an enclosing repository
- **WHEN** the base folder is only a subdirectory of a repository rooted outside it
- **THEN** the enclosing repository SHALL not become an actionable workspace repository through upward root lookup.

#### Scenario: Linked worktree has external metadata
- **WHEN** a valid working tree beneath the base folder references its Git metadata outside the base folder
- **THEN** the system SHALL allow that working tree while continuing to reject arbitrary client-supplied filesystem paths.
