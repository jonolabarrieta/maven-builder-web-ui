# async-git-refresh Specification

## Purpose
TBD - created by archiving change h2-lock-timeout-async-git-refresh. Update Purpose after archive.
## Requirements
### Requirement: Asynchronous Git Status Refresh

The system SHALL render the workspace detail page promptly with cached Git branch names, then request current Git status in the background and update only the displayed branch indicators and their contextual status. The background update SHALL NOT replace project rows, checkboxes, or sorting controls, and SHALL NOT show a blocking full-screen spinner during normal page load.

#### Scenario: Background refresh on page load
- **WHEN** the user opens a workspace detail page
- **THEN** the project list SHALL be usable immediately with cached branch names
- **AND** one background request SHALL start to obtain current branch names.

#### Scenario: Current branches arrive
- **WHEN** the background Git request completes successfully
- **THEN** each returned branch SHALL update only its matching project's branch indicator
- **AND** the existing project rows and their interactive controls SHALL remain in place
- **AND** the contextual Git status SHALL indicate completion.

#### Scenario: Git lookup fails
- **WHEN** the background Git request fails or is aborted without a replacement request
- **THEN** cached branch names SHALL remain visible
- **AND** the contextual Git status SHALL indicate that branch information could not be refreshed.

#### Scenario: Explicit refresh supersedes an older Git response
- **WHEN** an explicit workspace Refresh starts while the on-load Git request is pending
- **THEN** the older response SHALL NOT overwrite the result of the newer refresh.

### Requirement: Efficient branch lookup
The background Git status request SHALL query each distinct repository at most once, even when several displayed projects, or several source workspaces of a consumer, share that repository. For a repository on a branch, the lookup SHALL start a single Git process. Additional Git processes MAY be used only when HEAD is detached. Repositories SHALL be queried concurrently, with no more than a configurable number of simultaneous Git processes; the default limit SHALL be 6. A failure or timeout in one repository SHALL leave that repository's branch as `n/a` without failing the other repositories. The displayed label SHALL remain the short branch name, or the tag or short commit followed by ` · detached HEAD` when detached. A branch without commits SHALL show its branch name.

#### Scenario: Shared repository
- **WHEN** five displayed projects live in the same repository
- **THEN** the request SHALL run Git once for that repository and SHALL return its branch for all five projects.

#### Scenario: One process per branch checkout
- **WHEN** the request covers 75 repositories that are all on branches
- **THEN** the request SHALL start at most 75 Git processes.

#### Scenario: Bounded concurrency
- **WHEN** the request covers more repositories than the configured limit
- **THEN** no more than that limit of Git processes SHALL run at the same time, and the total time SHALL be shorter than querying them one after another.

#### Scenario: Detached HEAD keeps its label
- **WHEN** a repository is checked out at a tagged commit without a branch
- **THEN** its label SHALL be the tag name followed by ` · detached HEAD`, as before.

#### Scenario: One repository fails
- **WHEN** one repository's lookup fails or times out while others succeed
- **THEN** that repository SHALL report `n/a` and every other repository SHALL report its branch.

#### Scenario: New repository without commits
- **WHEN** a repository's current branch `main` has no commits yet
- **THEN** its label SHALL be `main`.
