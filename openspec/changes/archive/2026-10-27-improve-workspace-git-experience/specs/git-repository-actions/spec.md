## MODIFIED Requirements

### Requirement: Explicit repository commit from staged changes
The system SHALL create a commit for one selected repository using a non-blank user message and the staged snapshot the user reviewed. It SHALL reject the absence of staged changes, unresolved conflicts, and stale staged state; staged deletion of all tracked files SHALL remain a valid change, and before the first commit it SHALL use unborn-HEAD semantics. Commit SHALL not stage additional files, include unstaged edits implicitly, disable configured hooks, or automatically retry after failure. A plain Commit SHALL not push; pushing after commit SHALL happen only when the user explicitly chooses Commit & Push.

#### Scenario: Commit a partially staged file
- **WHEN** a repository contains staged edits plus additional unstaged/untracked content and the user submits a valid commit
- **THEN** only staged changes SHALL be committed and all unstaged/untracked content SHALL remain pending without an implicit push.

#### Scenario: Missing message, conflicts, or stale staged contents
- **WHEN** the user submits a blank message, there are unresolved conflicts, or the staged snapshot has changed
- **THEN** the commit SHALL not start and the view SHALL identify the reason and preserve the draft.

#### Scenario: First repository commit
- **WHEN** an unborn repository has staged content and a valid message
- **THEN** the system SHALL allow its initial commit without requiring a pre-existing HEAD.

#### Scenario: Commit deletion of all tracked files
- **WHEN** an existing repository has staged deletion of every tracked file
- **THEN** the system SHALL allow a commit of the empty resulting tree rather than mistake an empty index for an absence of changes.

#### Scenario: Commit fails or user continues typing
- **WHEN** a hook, signing setup, or Git command fails, or the user edits the draft while a successful commit is running
- **THEN** the view SHALL preserve the current draft, report the actual outcome, and refresh state; only an unchanged successfully submitted draft SHALL be cleared.

### Requirement: Repository synchronization actions
The system SHALL provide fetch, fast-forward-only pull, and normal non-forced push per repository and for explicitly selected repositories. It SHALL honor the user's Git remote/authentication configuration, SHALL not fetch automatically on page entry, and SHALL distinguish missing remote/upstream, divergence, authentication failure, timeout, and successful outcomes. When a pull is refused because of divergence, the system SHALL offer an explicit, confirmed pull with rebase for that repository; rebase SHALL never run without that confirmation, and a rebase that stops on conflicts SHALL be reported with the repository left in Git's rebase state for the user to resolve or abort. When a branch has no upstream, the system SHALL offer an explicit Publish branch action that pushes the current branch to a remote chosen by the user (defaulting to the only remote, or `origin` when several exist) and sets it as upstream.

#### Scenario: Fetch then pull with an upstream
- **WHEN** the user fetches or pulls a repository with a configured remote/upstream
- **THEN** the requested operation SHALL run only for its explicit targets, refresh their state, and report results; pull SHALL refuse divergence requiring a merge or rebase.

#### Scenario: Push rejects a remote update or lacks upstream
- **WHEN** a normal push is rejected or the branch lacks the required upstream
- **THEN** the system SHALL show actionable feedback without force pushing or silently selecting a remote destination.

#### Scenario: Publish a new branch
- **WHEN** the current branch has no upstream and the user confirms Publish branch with a chosen remote
- **THEN** the branch SHALL be pushed to that remote under the same name, configured as upstream, and the view SHALL show its ahead/behind state

#### Scenario: Repository without remotes
- **WHEN** the user opens Publish branch in a repository with no configured remote
- **THEN** the action SHALL be unavailable with an explanation and no push SHALL run

#### Scenario: Confirmed rebase pull
- **WHEN** a fast-forward pull is refused because of divergence and the user confirms pull with rebase
- **THEN** the system SHALL rebase local commits onto the upstream, and a conflict SHALL be reported with the repository left in rebase state

## ADDED Requirements

### Requirement: Commit and push
The commit form SHALL offer Commit & Push for staged commits and for selected-file commits in one or multiple repositories. Each target SHALL first commit with the existing commit rules and then perform a normal non-forced push of its current branch within the same operation. The outcome SHALL distinguish commit failure, commit success with push success, and commit success with push failure, naming the created commit. A push failure SHALL never undo or repeat the commit. If a target has no upstream, the push step SHALL report the no-upstream state and offer Publish branch rather than choose a remote silently. A keyboard shortcut SHALL trigger Commit & Push separately from plain Commit.

#### Scenario: Commit and push succeed
- **WHEN** the user submits Commit & Push for a repository with an upstream and the remote accepts
- **THEN** one commit SHALL be created and pushed and the outcome SHALL report both steps as successful

#### Scenario: Push rejected after commit
- **WHEN** the commit succeeds but the remote rejects the push
- **THEN** the outcome SHALL state that the commit exists locally and the push failed with the reason, the draft SHALL be cleared as committed, and no retry SHALL occur

#### Scenario: Multiple repositories
- **WHEN** selected files in three repositories are submitted with Commit & Push and one push fails
- **THEN** each repository SHALL report its own commit and push result, and the other two SHALL remain committed and pushed

### Requirement: Confirmed deletion of untracked files
The system SHALL allow deleting a single untracked file selected under Changes after an application-rendered confirmation naming the path and stating irreversibility. It SHALL validate that the path is still untracked, not ignored-tracked content, not a directory containing a repository, and inside the repository before deleting. Tracked files SHALL not be deletable through this action.

#### Scenario: Delete an untracked file
- **WHEN** the user confirms deletion of an untracked file
- **THEN** the file SHALL be removed from disk and disappear from Changes

#### Scenario: File became tracked before confirmation
- **WHEN** the file was staged after the confirmation dialog opened
- **THEN** the deletion SHALL be rejected as stale and the file kept
