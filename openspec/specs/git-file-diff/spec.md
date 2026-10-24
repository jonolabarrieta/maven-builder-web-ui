# git-file-diff Specification

## Purpose
Define coherent local Git comparisons for staged and unstaged changes, including supported text presentations and explicit handling of special content.

## Requirements

### Requirement: Correct local comparison bases
The system SHALL compare the index with the working tree for Changes and HEAD with the index for Staged Changes. It SHALL use an empty base for an untracked text file and empty-tree semantics when HEAD does not yet exist. The comparison SHALL identify repository, path, and selected area explicitly and SHALL not require a network operation.

#### Scenario: Local file differs from both HEAD and index
- **WHEN** a file has different HEAD, staged, and on-disk versions
- **THEN** each file-group comparison SHALL show only its respective transition rather than combining staged and unstaged changes.

#### Scenario: First commit preparation
- **WHEN** the repository has no initial commit and contains staged new files
- **THEN** their staged comparisons SHALL show additions against an empty base without failing HEAD resolution.

### Requirement: Split and unified comparison presentation
Supported text differences SHALL be available in side-by-side and unified modes with old/new line numbers, clear additions/deletions, and navigation between change blocks. Both modes SHALL represent the same comparison and support selecting applicable added and deleted lines for partial staging actions.

#### Scenario: Switch comparison mode with a selection
- **WHEN** a user switches between split and unified mode for the current comparison
- **THEN** the represented changes and any still-valid selected change identities SHALL remain consistent.

#### Scenario: Text file is deleted or renamed
- **WHEN** the selected change deletes a text file or renames it
- **THEN** the comparison SHALL retain the old/new path and content identities and show the relevant deletion or rename information without treating a missing file as a generic read failure.

### Requirement: Coherent content and exact Git transport
Diff generation SHALL preserve supported content and path identities, including CRLF and missing-final-newline information. It SHALL use machine-readable Git output independently of command logs, shell interpretation, external diff programs, and text-conversion filters. The comparison SHALL include an identity/version snapshot of its source data and SHALL reject or retry a read whose inputs change during construction.

#### Scenario: Special characters and end-of-file changes
- **WHEN** a supported path includes whitespace, quotes, Unicode, or newlines and its text contains CRLF or lacks a final newline
- **THEN** the system SHALL address the intended path and represent those changes without filename splitting or content normalization.

#### Scenario: File changes while its diff is loaded
- **WHEN** source content or relevant repository state changes during comparison construction
- **THEN** the system SHALL obtain a coherent replacement snapshot or report that the comparison must be refreshed rather than return inconsistent selection coordinates.

### Requirement: Explicit handling of unsupported or special content
Binary files, oversized files/diffs, unsupported encodings, symlinks, mode-only changes, Git submodule pointers, and unresolved conflicts SHALL receive explicit content/metadata states. The system SHALL not silently truncate content or offer partial staging where it cannot honor the selection. File-level actions SHALL remain available when supported by Git, and symlink handling SHALL not read arbitrary target files outside the repository.

#### Scenario: Binary or oversized comparison
- **WHEN** the selected entry cannot be rendered as a supported text diff
- **THEN** the view SHALL show its path, status, reason, and available file-level actions, with partial-selection controls disabled.

#### Scenario: Conflicted file
- **WHEN** the selected path has unresolved index entries
- **THEN** the view SHALL explain that resolution is required and SHALL not represent the conflict as an ordinary two-version diff eligible for partial staging.
