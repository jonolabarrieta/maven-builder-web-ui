## MODIFIED Requirements

### Requirement: Workspace-wide Git repository discovery
The system SHALL discover every supported Git working-tree root at or beneath the workspace base folder independently of Maven registration, Maven scan exclusions, project selection, and skipped-project state. On entry to the Git view the system SHALL present the most recent discovery result for the workspace when one exists, and SHALL run a fresh discovery when none exists or through an explicit Refresh repositories action. Discovery SHALL not fetch remotes.

#### Scenario: Non-Maven and skipped projects are included
- **WHEN** the base folder contains a clean non-Maven repository, a modified Maven repository, and a repository whose registered projects are all skipped
- **THEN** all three repositories SHALL appear in the Git view without creating or changing Maven project registrations.

#### Scenario: Base folder is a repository
- **WHEN** the configured base folder is itself a Git working-tree root
- **THEN** discovery SHALL include that repository and continue scanning its subdirectories for other repositories.

#### Scenario: Reopen the Git view
- **WHEN** the user opens the Git view again after a completed discovery for the same workspace and base folder
- **THEN** the previous repository list SHALL be shown without a new full scan, and Refresh repositories SHALL still run a full scan

#### Scenario: Base folder changed
- **WHEN** the workspace base folder differs from the one used by the stored discovery result
- **THEN** the stored result SHALL be ignored and a fresh discovery SHALL run

### Requirement: Bounded discovery with explicit outcomes
Discovery SHALL avoid following directory symlinks or entering Git administrative directories, SHALL not stop at a repository root, and SHALL report unreadable/unsupported candidates or resource-limit interruptions. Discovery SHALL skip directories whose names match a configurable exclusion list that by default contains common build output and dependency folders (`target`, `node_modules`, `build`, `dist`, `out`, `.idea`, `.gradle`, `.mvn`); repositories located only inside excluded folders SHALL not be discovered. Long scans SHALL expose progress without blocking the rest of the page. Incomplete results SHALL be labelled as incomplete rather than represented as a complete scan.

#### Scenario: Unreadable directory during a long scan
- **WHEN** a scan discovers repositories but cannot inspect another subtree
- **THEN** discovered repositories SHALL remain usable and the view SHALL show the unreadable path warning and scan completion state.

#### Scenario: Missing base folder or no repositories
- **WHEN** the base folder is missing, invalid, or contains no repositories
- **THEN** the view SHALL distinguish configuration/access failure from a successful empty scan and offer an appropriate settings or refresh action.

#### Scenario: Large build output folders
- **WHEN** repositories contain `target` and `node_modules` folders with many subdirectories
- **THEN** discovery SHALL not descend into those folders and SHALL count no visits for them

## ADDED Requirements

### Requirement: Automatic rediscovery of unknown identities
When a repository request fails because the server does not know the repository identity, for example after a server restart, the view SHALL run one discovery automatically and retry the original read once if the repository is found again. Mutations SHALL not be retried automatically; the user SHALL be asked to review the refreshed state.

#### Scenario: Server restarted while the page was open
- **WHEN** the user selects a file after the server restarted
- **THEN** the view SHALL rediscover repositories and load the comparison without requiring a manual refresh

#### Scenario: Mutation after restart
- **WHEN** a stage request fails because the repository identity is unknown
- **THEN** the view SHALL rediscover repositories and ask the user to repeat the action after reviewing the refreshed state
