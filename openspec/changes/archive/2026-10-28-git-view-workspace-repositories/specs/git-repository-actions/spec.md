## REMOVED Requirements

### Requirement: Explicit repository commit from staged changes
**Reason**: The commit scope selector is removed. Commits always consist of the files the user marked, so a separate "commit everything staged" mode duplicates that path and hides what is committed.
**Migration**: Mark the files to commit, or check the repository to mark all of its files. Staged content that is not marked stays staged and is not committed. To conclude a merge, rebase, cherry-pick or revert, use Open in VS Code or a terminal.

## MODIFIED Requirements

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
