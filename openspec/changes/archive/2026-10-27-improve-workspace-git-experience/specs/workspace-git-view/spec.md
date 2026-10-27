## MODIFIED Requirements

### Requirement: Refresh preserves active interaction state
The system SHALL refresh status on explicit request, after operations, and when returning to a visible/focused Git page. Returning to the page SHALL refresh the active repository immediately and other repositories only when their last status is older than a configured interval, spreading those refreshes over time. It SHALL ignore superseded responses and preserve the selected repository/file/area, comparison mode, scroll position, and per-repository commit drafts whenever their targets remain available. Diff loading SHALL be limited to the selected file.

#### Scenario: Late refresh arrives while editing a commit message
- **WHEN** an older status response arrives after a newer response while the user edits a commit draft
- **THEN** the older response SHALL not overwrite current status, input contents, selection, or keyboard focus.

#### Scenario: Selected file disappears after staging
- **WHEN** an operation removes the selected entry from its current file group
- **THEN** the view SHALL refresh the affected repository, explain the state change, and select a predictable adjacent entry or show an appropriate empty comparison state.

#### Scenario: Navigate away with a draft
- **WHEN** the user leaves the Git page with a non-empty commit draft
- **THEN** application navigation SHALL offer an application-rendered confirmation before discarding the draft.

#### Scenario: Quick window switch
- **WHEN** the user switches away and back to the Git page within the refresh interval
- **THEN** only the active repository SHALL be refreshed

## ADDED Requirements

### Requirement: Stage and unstage from the file list
Each eligible file entry SHALL expose stage (Changes) or unstage (Staged Changes) controls, and each group heading SHALL expose stage-all or unstage-all for its eligible files, without opening the comparison. These controls SHALL use the same snapshot validation as comparison-pane actions and SHALL be unavailable for conflicts and submodule entries.

#### Scenario: Stage a file from the list
- **WHEN** the user activates the stage control of a file under Changes
- **THEN** the whole file SHALL be staged and the list SHALL refresh, keeping the current comparison selection when it still exists

### Requirement: Keyboard shortcuts
The Git view SHALL provide documented keyboard shortcuts, active only when focus is not in a text input or editor, for: next and previous file, stage or unstage the current file, next and previous change block, toggle the sidebar, focus the commit message, and submit Commit and Commit & Push from the message field. A help control SHALL list the shortcuts.

#### Scenario: Navigate files with the keyboard
- **WHEN** the comparison has focus and the user presses the next-file shortcut
- **THEN** the next file entry in the list SHALL be selected and its comparison loaded

#### Scenario: Typing in the commit message
- **WHEN** focus is in the commit message field and the user types a shortcut letter
- **THEN** the letter SHALL be inserted and no shortcut action SHALL run

### Requirement: Branch switching from the Git view
The active repository header SHALL show the current branch as a control that opens the existing reference selector for that repository, with the same checkout confirmation and safety rules. It SHALL also allow creating a new local branch from the current HEAD. After a checkout the repository status and history SHALL refresh.

#### Scenario: Switch branch
- **WHEN** the user selects another branch from the header and confirms
- **THEN** the repository SHALL check out that branch under the existing safety rules and the view SHALL refresh its status

#### Scenario: Create a branch
- **WHEN** the user creates a new branch with a valid name
- **THEN** the branch SHALL be created from HEAD and checked out, and an invalid or existing name SHALL be rejected with an explanation

### Requirement: Open repository in editor
The active repository header SHALL offer an action to open the repository root in Visual Studio Code using the existing local project shortcut behavior, to support conflict resolution and larger edits outside the Git view.

#### Scenario: Open in VS Code
- **WHEN** the user activates Open in VS Code
- **THEN** VS Code SHALL open at the repository root with the path passed literally
