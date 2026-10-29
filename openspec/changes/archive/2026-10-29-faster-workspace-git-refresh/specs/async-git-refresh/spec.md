# Spec Delta

## ADDED Requirements

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
