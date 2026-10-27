# git-file-editing Specification

## Purpose
Allow last-minute corrections to a changed file's working-tree content directly from the Git view, without risking overwriting concurrent edits.

## Requirements

### Requirement: Edit working-tree content
For a text file shown under Changes, the view SHALL offer an Edit action that opens its current working-tree content in an editor with syntax highlighting. Saving SHALL write the content to the working-tree file only and SHALL not stage, commit or change the index. Editing SHALL not be offered for Staged Changes entries, conflicts, binary, oversized, symlink or submodule entries, or files using Git content filters or working-tree encoding conversion.

#### Scenario: Fix a typo before committing
- **WHEN** the user edits a changed Java file and saves
- **THEN** the working-tree file SHALL contain the new content, the comparison and status SHALL refresh, and the index SHALL be unchanged

#### Scenario: Unsupported entry
- **WHEN** the selected entry is binary, oversized, a symlink, a submodule or a conflict
- **THEN** the Edit action SHALL be unavailable with an explanation

### Requirement: Optimistic concurrency for saves
A save SHALL include the content identity the editor was opened with. If the file changed on disk since then, the save SHALL be rejected without writing, and the user SHALL be offered to reload or copy their edits.

#### Scenario: File changed externally
- **WHEN** another tool modifies the file after the editor opened it and the user saves
- **THEN** the save SHALL be rejected, the file on disk SHALL keep the external change, and the user's edits SHALL remain in the editor

### Requirement: Preserve file conventions
Saving SHALL preserve the file's existing line-ending style, byte-order mark and presence or absence of a final newline unless the user explicitly changes them. The write SHALL be atomic, SHALL be limited to repository-relative paths inside the repository, and SHALL respect the configured file size limit.

#### Scenario: CRLF file
- **WHEN** the user edits a file that uses CRLF line endings and saves
- **THEN** every line in the saved file SHALL still end with CRLF

#### Scenario: Path outside the repository
- **WHEN** a save request names a traversal, administrative or out-of-repository path
- **THEN** the request SHALL be rejected without writing

### Requirement: Unsaved edits are protected
Switching file, repository or leaving the page with unsaved editor changes SHALL require an application-rendered confirmation.

#### Scenario: Switch file with unsaved edits
- **WHEN** the editor has unsaved changes and the user selects another file
- **THEN** a confirmation SHALL ask whether to discard the edits, and cancelling SHALL keep the editor open
