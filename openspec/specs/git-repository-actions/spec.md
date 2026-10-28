# git-repository-actions Specification

## Purpose
Define repository commits, synchronization, stash, unstage and discard operations with coordinated mutations and independently observable outcomes.

## Requirements

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
The system SHALL commit only the files the user marked, in one or several repositories, with a common non-blank message. Each repository SHALL produce its own commit and outcome. Files marked from Changes SHALL use their working-tree content, and files marked from Staged Changes SHALL use their index content. The same path SHALL have only one marked source. Staged and unstaged content that is not marked SHALL remain outside the commit, keeping its index entries and working-tree contents. Marked staged files with additional unstaged edits SHALL keep those edits. Before running, Commit and Commit & Push SHALL open an application-rendered confirmation dialog that lists each target repository by name with its marked files and states whether a push will follow. Cancelling SHALL run nothing. The confirmed snapshot SHALL be validated before each commit. Commits SHALL support unborn repositories and staged deletion of every tracked file, SHALL not disable configured hooks and SHALL not retry automatically.

#### Scenario: Only some files are committed
- **WHEN** the user marks two changed files and leaves a third staged file and another unstaged file unmarked
- **THEN** the new commit SHALL contain only the chosen versions of the two marked files, and the staged and unstaged content that was left out SHALL be kept

#### Scenario: Staged but unmarked file
- **WHEN** a repository has a staged file that the user did not mark and the user commits other marked files
- **THEN** the staged file SHALL not be committed and SHALL remain staged

#### Scenario: Select a partially staged file from Staged Changes
- **WHEN** a marked file has different staged and working-tree versions and is marked from Staged Changes
- **THEN** the commit SHALL use its staged version and retain its additional working-tree edits

#### Scenario: Selected tracked file beneath an ignored directory
- **WHEN** a file marked from Changes is already tracked in HEAD or the real index and its directory is now ignored
- **THEN** the commit SHALL use that marked working-tree version without rejecting the ignored parent or adding ignored untracked files nearby, preserving excluded staging

#### Scenario: Commit selected files across repositories
- **WHEN** marked files belong to two repositories and the user activates Commit
- **THEN** a confirmation dialog SHALL list both repositories with their files, and only after confirmation each SHALL receive a separate commit with the common message

#### Scenario: Cancel the confirmation
- **WHEN** the user cancels the commit confirmation dialog
- **THEN** no commit SHALL be created, and the draft and selections SHALL be kept

#### Scenario: First repository commit
- **WHEN** an unborn repository has marked files and a valid message
- **THEN** the system SHALL create its initial commit

#### Scenario: Stale selection or failed hook
- **WHEN** a repository changes after confirmation or its commit hook fails
- **THEN** that target SHALL report failure without broadening the selection, changing unrelated index entries, deleting working-tree edits or retrying, and failures SHALL not undo successful commits in other repositories

#### Scenario: Selected commit during an unfinished merge
- **WHEN** a commit targets a repository with an unfinished merge, rebase, cherry-pick or revert
- **THEN** that target SHALL be refused with an explanation that the operation must be completed outside the Git view first

### Requirement: Commit and push
The commit form SHALL offer Commit & Push for marked-file commits in one or several repositories, confirmed through the same dialog as Commit. Each target SHALL first commit under the existing commit rules and then perform a normal non-forced push of its current branch within the same operation. The outcome SHALL distinguish commit failure, commit success with push success, and commit success with push failure, naming the created commit. A push failure SHALL never undo or repeat the commit. If a target has no upstream, the push step SHALL report the no-upstream state and offer Publish branch rather than choose a remote silently. A keyboard shortcut SHALL open Commit & Push confirmation separately from plain Commit.

#### Scenario: Commit and push succeed
- **WHEN** the user confirms Commit & Push for a repository with an upstream and the remote accepts
- **THEN** one commit SHALL be created and pushed and the outcome SHALL report both steps as successful

#### Scenario: Push rejected after commit
- **WHEN** the commit succeeds but the remote rejects the push
- **THEN** the outcome SHALL state that the commit exists locally and the push failed with the reason, the draft SHALL be cleared as committed, and no retry SHALL occur

#### Scenario: Multiple repositories
- **WHEN** marked files in three repositories are confirmed with Commit & Push and one push fails
- **THEN** each repository SHALL report its own commit and push result, and the other two SHALL remain committed and pushed

### Requirement: Confirmed deletion of untracked files
The system SHALL allow deleting a single untracked file selected under Changes after an application-rendered confirmation naming the path and stating irreversibility. It SHALL validate that the path is still untracked, not ignored-tracked content, not a directory containing a repository, and inside the repository before deleting. Tracked files SHALL not be deletable through this action.

#### Scenario: Delete an untracked file
- **WHEN** the user confirms deletion of an untracked file
- **THEN** the file SHALL be removed from disk and disappear from Changes

#### Scenario: File became tracked before confirmation
- **WHEN** the file was staged after the confirmation dialog opened
- **THEN** the deletion SHALL be rejected as stale and the file kept
