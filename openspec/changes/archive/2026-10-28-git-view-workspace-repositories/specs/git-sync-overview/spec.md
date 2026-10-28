## MODIFIED Requirements

### Requirement: Synchronization overview
The Git view SHALL provide a Synchronization control that opens an overview of every discovered repository. Each repository SHALL be presented as a header row showing its folder name without a path, its current branch as a prominent label (or a detached or unborn state), the current branch's upstream, ahead and behind counts, the pending local change count and the time of the last fetch or that no fetch was recorded. The repository's other local branches SHALL be listed beneath the header with their upstream or no-upstream state, ahead and behind counts and a divergence marker when both are non-zero. The header SHALL always reflect the current branch, whatever the order or filtering of the other branches. Non-current branches SHALL be collapsible per repository. The overview SHALL be built from local data without contacting remotes and SHALL be returned for all repositories in one request.

#### Scenario: Current branch is not alphabetically first
- **WHEN** a repository has branches `develop` and `master` and `master` is checked out
- **THEN** the repository header SHALL show `master` as its current branch, and `develop` SHALL be listed beneath it

#### Scenario: Repositories needing push and pull
- **WHEN** one repository's current branch is 2 ahead and another's is 3 behind
- **THEN** the overview SHALL show 2 to push for the first and 3 to pull for the second

#### Scenario: Non-current branch ahead
- **WHEN** a non-current local branch has unpushed commits
- **THEN** the overview SHALL list that branch beneath its repository with its ahead count

#### Scenario: Diverged branch
- **WHEN** a branch is both ahead and behind its upstream
- **THEN** the overview SHALL mark it as diverged and SHALL indicate that fast-forward pull will not apply

#### Scenario: Detached HEAD
- **WHEN** a repository is in detached HEAD state
- **THEN** its header SHALL show a detached state instead of naming a branch

### Requirement: Filtering and bulk synchronization
The overview SHALL filter by ahead, behind, diverged, no upstream and local changes. It SHALL also filter by text matching the repository name and by text matching branch names; the filters combine. A repository whose branches do not match the branch filter SHALL be hidden. Status filters SHALL keep the repository header visible when any of its branches match. The overview SHALL offer Fetch all, Pull behind and Push ahead actions that target only the current branches of the repositories shown by the active filters. Each action SHALL produce independent per-repository outcomes and refresh the overview afterwards. Actions SHALL use the existing pull and push safety rules.

#### Scenario: Filter by repository name
- **WHEN** the user types `billing` in the repository filter
- **THEN** only repositories whose name contains `billing`, ignoring case, SHALL be shown

#### Scenario: Filter by branch name
- **WHEN** the user types `release` in the branch filter
- **THEN** only branches whose name contains `release` SHALL be listed, under headers of their repositories

#### Scenario: Push every repository that is ahead
- **WHEN** the user activates Push ahead
- **THEN** a push SHALL run only for shown repositories whose current branch is ahead, and each result SHALL be reported separately

#### Scenario: Fetch all then review
- **WHEN** the user activates Fetch all
- **THEN** every shown repository SHALL be fetched with independent outcomes and the overview SHALL refresh with updated counts and fetch times

## ADDED Requirements

### Requirement: Resizable synchronization dialog
The Synchronization dialog SHALL open large enough to use most of the viewport and SHALL let the user resize it. The table SHALL scroll within the dialog with sticky column headings. The chosen size SHALL persist in the browser.

#### Scenario: Resize the dialog
- **WHEN** the user drags the dialog's resize handle
- **THEN** the dialog SHALL change size within the viewport and the table SHALL use the available space

#### Scenario: Reopen after resizing
- **WHEN** the user closes and reopens the dialog or reloads the page
- **THEN** the dialog SHALL open with the previously chosen size
