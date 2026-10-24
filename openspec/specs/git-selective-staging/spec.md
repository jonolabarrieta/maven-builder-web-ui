# git-selective-staging Specification

## Purpose
Define whole-file and partial Git staging and unstaging while preserving unrelated content and validating the reviewed source snapshot.

## Requirements

### Requirement: Whole-file stage and unstage
The system SHALL support staging and unstaging an individual file change without modifying unrelated paths. Whole-file staging SHALL handle added, modified, deleted, and renamed files and supported special-file metadata. Unstage SHALL preserve working-tree contents and SHALL work before the initial commit. Rename pairs SHALL be handled atomically.

#### Scenario: Stage one file among several changes
- **WHEN** a user stages one modified file
- **THEN** its current supported change SHALL enter the index while unrelated staged and unstaged paths remain unchanged.

#### Scenario: Update a tracked file beneath an ignored directory
- **WHEN** a user stages a modified or deleted tracked file beneath a directory matched by an ignore rule
- **THEN** its tracked change SHALL enter the index while ignored untracked files remain excluded.

#### Scenario: Unstage a newly added file before the first commit
- **WHEN** a user unstages a newly added file in an unborn repository
- **THEN** the file SHALL be removed from the index and its working-tree contents SHALL remain on disk.

#### Scenario: Stage an externally resolved conflict
- **WHEN** a user explicitly stages a whole conflict file after editing its resolution externally
- **THEN** Git SHALL stage the supplied file as the user's resolution and the view SHALL refresh its conflict status without claiming to have generated a resolution.

### Requirement: Stage selected text lines and blocks
For supported ordinary text changes, including new text files, the system SHALL allow staging complete change blocks or individual selected added/deleted lines, including selections within one replacement block. Only the selected change SHALL be applied to the index; unselected staged content and the complete working-tree file SHALL remain intact.

#### Scenario: Select one addition within a change block
- **WHEN** two added lines belong to the same diff block and only one is selected for staging
- **THEN** the index SHALL contain only the selected addition and the unselected addition SHALL remain under Changes.

#### Scenario: Select a deletion or part of a replacement
- **WHEN** a user selects a deleted line or a valid subset of added/deleted lines in a replacement
- **THEN** the resulting index SHALL reflect exactly that selection, preserving all other index and working-tree content.

#### Scenario: Partially stage a new file
- **WHEN** a user selects only some added lines in a new text file
- **THEN** the index SHALL contain the selected new-file content and the full file SHALL remain on disk, with remaining edits visible under Changes.

### Requirement: Unstage selected text lines and blocks
For supported text changes, the system SHALL allow reversing selected staged lines or blocks against HEAD without changing the working-tree file or unrelated staged changes. Removing the final staged addition of a new file SHALL retain correct new/untracked-file semantics.

#### Scenario: Unstage one of several staged edits
- **WHEN** a user unstages one selected change from a file with multiple staged edits
- **THEN** that change SHALL leave the index, other staged edits SHALL remain, and the file on disk SHALL be unchanged.

#### Scenario: Unstage the last new-file addition
- **WHEN** the user unstages all remaining staged additions of a newly added file
- **THEN** the new file SHALL no longer be staged and its complete working-tree contents SHALL be preserved.

### Requirement: Snapshot-validated server-owned selections
Every stage/unstage request SHALL identify the repository, path, comparison area, and expected snapshot. Partial requests SHALL reference server-issued change identities rather than arbitrary client patch text. The system SHALL validate the current relevant HEAD, index, working-tree contents, metadata, and selection before applying the change. Stale or invalid requests SHALL not apply the requested mutation and SHALL return actionable refresh feedback.

#### Scenario: External edit invalidates selected lines
- **WHEN** a file changes after its diff was displayed and the user submits the old selection
- **THEN** the endpoint SHALL return a stale-comparison conflict, leave the requested mutation unapplied, and refresh the comparison before allowing a new selection.

#### Scenario: Index changes in another tool
- **WHEN** the relevant index state changes before staging or unstaging the selected snapshot
- **THEN** the system SHALL reject that selection rather than apply it against the replacement index state.

#### Scenario: Invalid selection or injected path
- **WHEN** a request supplies unknown line identifiers, another repository's snapshot, traversal, or Git administrative paths
- **THEN** the request SHALL be rejected without running an arbitrary patch or modifying any file/index entry.

### Requirement: Explicit partial-action limitations and atomic failure
The system SHALL disable partial actions for unsupported content, rename pairs, unresolved conflicts, and metadata-only changes, explaining the available whole-file alternative. Patch failure, lock contention, and temporary-index failure SHALL leave the requested change unapplied, release app-owned resources, and report an error without falling back to staging a whole file or resetting the repository.

#### Scenario: Unsupported partial selection
- **WHEN** a selected entry is binary, oversized, a rename pair, or an unresolved conflict
- **THEN** partial stage/unstage controls SHALL be unavailable with an explanation, while supported whole-file controls remain accessible.

#### Scenario: Selected patch cannot be applied
- **WHEN** Git rejects a prepared partial mutation or its index lock cannot be acquired
- **THEN** the system SHALL report failure, preserve unrelated index/working-tree state, and refresh the affected repository without retrying as a broader action.
