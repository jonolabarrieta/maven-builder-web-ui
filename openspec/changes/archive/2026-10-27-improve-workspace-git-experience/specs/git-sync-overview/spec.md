# Spec Delta

## Purpose

Show at a glance which workspace repositories and branches have commits to push or pull, and synchronize them in bulk.

## ADDED Requirements

### Requirement: Synchronization overview
The Git view SHALL provide a Synchronization control that opens an overview of every discovered repository. For each local branch the overview SHALL show the branch name, whether it is current, its upstream or a no-upstream state, commits ahead, commits behind and a divergence marker when both are non-zero. Per repository, it SHALL show the pending local change count and the time of the last fetch, or that no fetch has been recorded. The overview SHALL be built from local data without contacting remotes and SHALL be returned for all repositories in one request.

#### Scenario: Repositories needing push and pull
- **WHEN** one repository's current branch is 2 ahead and another's is 3 behind
- **THEN** the overview SHALL show 2 to push for the first and 3 to pull for the second

#### Scenario: Non-current branch ahead
- **WHEN** a non-current local branch has unpushed commits
- **THEN** the overview SHALL list that branch with its ahead count

#### Scenario: Diverged branch
- **WHEN** a branch is both ahead and behind its upstream
- **THEN** the overview SHALL mark it as diverged and SHALL indicate that fast-forward pull will not apply

### Requirement: Freshness disclosure
The overview SHALL state that ahead and behind counts reflect the last fetch and SHALL visually flag repositories whose last fetch is older than a configured age or unknown.

#### Scenario: Stale fetch
- **WHEN** a repository was last fetched several days ago
- **THEN** its row SHALL be flagged as possibly outdated

### Requirement: Filtering and bulk synchronization
The overview SHALL filter by ahead, behind, diverged, no upstream and local changes. It SHALL offer Fetch all, Pull behind and Push ahead actions that target only the matching current branches. Each action SHALL produce independent per-repository outcomes and refresh the overview afterwards. Actions SHALL use the existing pull and push safety rules.

#### Scenario: Push every repository that is ahead
- **WHEN** the user activates Push ahead
- **THEN** a push SHALL run only for repositories whose current branch is ahead, and each result SHALL be reported separately

#### Scenario: Fetch all then review
- **WHEN** the user activates Fetch all
- **THEN** every repository SHALL be fetched with independent outcomes and the overview SHALL refresh with updated counts and fetch times
