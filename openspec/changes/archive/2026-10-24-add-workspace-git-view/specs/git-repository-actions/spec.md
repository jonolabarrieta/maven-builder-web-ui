## ADDED Requirements

### Requirement: Explicit repository commit from staged changes
The system SHALL create a commit for one selected repository using a non-blank user message and the staged snapshot the user reviewed. It SHALL reject the absence of staged changes, unresolved conflicts, and stale staged state; staged deletion of all tracked files SHALL remain a valid change, and before the first commit it SHALL use unborn-HEAD semantics. Commit SHALL not stage additional files, include unstaged edits implicitly, push, disable configured hooks, or automatically retry after failure.

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
The system SHALL provide fetch, fast-forward-only pull, and normal non-forced push per repository and for explicitly selected repositories. It SHALL honor the user's Git remote/authentication configuration, SHALL not fetch automatically on page entry, and SHALL distinguish missing remote/upstream, divergence, authentication failure, timeout, and successful outcomes.

#### Scenario: Fetch then pull with an upstream
- **WHEN** the user fetches or pulls a repository with a configured remote/upstream
- **THEN** the requested operation SHALL run only for its explicit targets, refresh their state, and report results; pull SHALL refuse divergence requiring a merge or rebase.

#### Scenario: Push rejects a remote update or lacks upstream
- **WHEN** a normal push is rejected or the branch lacks the required upstream
- **THEN** the system SHALL show actionable feedback without force pushing or silently selecting a remote destination.

### Requirement: Repository stash and unstage-all
The system SHALL provide stash and unstage-all per repository and for selected repositories. Stash SHALL use the reusable confirmation dialog with an unchecked include-untracked checkbox controlling whether untracked files are included. Unstage-all SHALL preserve working-tree contents, including in an unborn repository. These selections SHALL be independent of Maven enabled/skipped state.

#### Scenario: Stash confirmation excludes untracked files by default
- **WHEN** the user confirms stash without changing the checkbox
- **THEN** selected repositories SHALL stash tracked changes while retaining untracked files.

#### Scenario: Include untracked or cancel stash
- **WHEN** the user checks include-untracked and confirms, or cancels the stash dialog
- **THEN** the confirmed action SHALL include untracked files, while cancellation SHALL issue no stash mutation.

#### Scenario: Unstage all before the first commit
- **WHEN** a user requests unstage-all in an unborn repository
- **THEN** the index SHALL become empty while every working-tree file remains intact.

### Requirement: Confirmed discard of tracked unstaged changes
The system SHALL support discarding one tracked file's unstaged changes or all tracked unstaged changes in explicitly selected repositories, restoring from the index. It SHALL preserve staged changes and untracked files, require application-rendered confirmation naming the targets and irreversibility, and validate the confirmed snapshot. Repository-wide discard SHALL require typing the workspace name. Deletion of untracked files SHALL not be offered by this action.

#### Scenario: Discard one partially staged file
- **WHEN** the user confirms discard for a tracked file with staged and unstaged edits
- **THEN** its working-tree content SHALL be restored from its index entry while staged changes, unrelated files, and untracked files remain unchanged.

#### Scenario: Confirm or cancel multi-repository discard
- **WHEN** the user selects repositories and opens repository-wide discard
- **THEN** the dialog SHALL identify the target count, require the exact workspace name, and run only after confirmation; cancellation SHALL change nothing.

#### Scenario: Confirmed discard becomes stale
- **WHEN** a relevant working-tree/index snapshot changes after discard confirmation was prepared
- **THEN** the system SHALL reject the stale discard and require refreshed review and confirmation instead of discarding newly changed content.

### Requirement: Coordinated mutations and isolated bulk outcomes
All application Git mutation paths targeting the same Git common directory SHALL share a coordinator, including existing project actions, while distinct repositories SHALL execute through bounded concurrency. Bulk requests SHALL deduplicate identities, validate every target, and produce separate terminal outcomes. Successful targets SHALL not be rolled back or re-executed because another target failed.

#### Scenario: New view overlaps an existing project Git action
- **WHEN** an existing project control and the Git view request mutations for modules in the same repository
- **THEN** those mutations SHALL serialize through the shared coordinator while retaining each interface's existing selection and command semantics.

#### Scenario: Linked worktrees and independent repositories
- **WHEN** two targets share a Git common directory and a third belongs to an independent repository
- **THEN** mutations for the shared directory SHALL serialize while the independent repository can proceed within the worker limit.

#### Scenario: Duplicate bulk targets and partial failure
- **WHEN** a bulk request repeats one repository identity and another valid repository fails
- **THEN** the duplicate repository SHALL run at most once and feedback SHALL identify each valid target's result independently.

### Requirement: Observable bounded operations
Long discovery and repository actions SHALL expose workspace-scoped operation identities, progress, and per-repository terminal status without blocking page interaction. Commands SHALL use bounded output and timeouts without interactive credential prompts. Failures SHALL clear busy states and provide useful error guidance. A possibly completed timed-out/interrupted commit or remote mutation SHALL be reported as uncertain pending refresh and SHALL not be retried automatically. Page entry SHALL not trigger Git mutations or remote Git operations.

#### Scenario: Operation times out or credentials cannot be obtained
- **WHEN** a remote command cannot finish within its deadline or requires unavailable authentication
- **THEN** the operation SHALL terminate or report its uncertain outcome as appropriate, expose useful feedback, release app-owned execution resources, and refresh repository state without an automatic retry.

#### Scenario: Poll another workspace's operation
- **WHEN** a request supplies an operation identity owned by a different workspace
- **THEN** the endpoint SHALL reject access rather than expose its paths, logs, or results.

### Requirement: Commit selected files in one or multiple repositories
The system SHALL retain the current single-repository staged commit and provide selected-file commits for one or multiple repositories with a common non-blank message. Each repository SHALL produce its own commit and outcome. Files selected from Changes SHALL use working-tree content; files selected from Staged Changes SHALL use index content. The same path SHALL have only one selected source. Unselected files SHALL remain outside the commit, preserving their logical index entries and working-tree contents. Selected staged files with additional unstaged edits SHALL retain those edits.

#### Scenario: Only some files are committed
- **WHEN** the user selects two changed files and leaves a third staged file and another unstaged file unselected
- **THEN** the new commit SHALL contain only the chosen versions of the two selected files, and excluded staging/working-tree contents SHALL be preserved.

#### Scenario: Select a partially staged file from Staged Changes
- **WHEN** a selected file has different staged and working-tree versions and is selected from Staged Changes
- **THEN** the commit SHALL use its staged version and retain its additional working-tree edits.

#### Scenario: Selected tracked file beneath an ignored directory
- **WHEN** a file selected from Changes is already tracked in HEAD or the real index and its directory is now ignored
- **THEN** the commit SHALL use that selected working-tree version without rejecting the ignored parent or adding ignored untracked files nearby, preserving excluded staging.

#### Scenario: Commit selected files across repositories
- **WHEN** selected files belong to two repositories and the user chooses the multiple-repository commit mode
- **THEN** each target SHALL receive a separate commit with the common message, repositories without selected files SHALL not be targeted, and failures SHALL not undo successful commits.

#### Scenario: Stale selection or failed hook
- **WHEN** a repository changes before execution or its commit hook fails
- **THEN** that target SHALL report failure without broadening the selection, changing unrelated index entries, deleting working-tree edits, or automatically retrying.

#### Scenario: Selected commit during an unfinished merge
- **WHEN** a selected-file commit targets a repository with an unfinished merge, rebase, cherry-pick, or revert
- **THEN** the operation SHALL require completing that operation first, while the existing normal staged commit remains available where Git supports it.
