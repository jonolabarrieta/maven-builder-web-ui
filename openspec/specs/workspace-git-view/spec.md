# workspace-git-view Specification

## Purpose
Define the workspace Git interface for repository navigation, change review, file selection, commit scope and accessible operation feedback.

## Requirements

### Requirement: Dedicated workspace Git navigation
The system SHALL provide Projects and Git navigation within a workspace and a dedicated Git page with repository/file navigation, a comparison pane, repository actions, and a repository-specific commit form. Application labels SHALL be English and follow the existing application styling and dialog conventions.

#### Scenario: Open Git from Projects
- **WHEN** the user activates Git in the workspace navigation
- **THEN** the Git page SHALL open in the same workspace, discover its repositories, and provide navigation back to Projects.

### Requirement: Repository overview and independent selection
The view SHALL list clean and changed repositories with display paths, branch or detached/unborn state, upstream availability, cached-remote ahead/behind counts when available, and pending-file counts. It SHALL offer search and an optional changed-only filter. Git repository selections SHALL be independent of Maven project/build selections.

#### Scenario: Select repositories for an action
- **WHEN** the user selects two repositories in the Git view
- **THEN** the action target SHALL consist of those repositories regardless of the associated Maven projects' selected or skipped state.

#### Scenario: Upstream is absent
- **WHEN** a repository has no configured upstream
- **THEN** the view SHALL show a no-upstream state rather than implying that it is synchronized or displaying fabricated ahead/behind counts.

### Requirement: Distinct file groups and partial staging visibility
Each repository SHALL separate Changes, Staged Changes, and Conflicts. Changes SHALL include tracked unstaged and untracked files with distinguishable status labels. A file with both staged and unstaged edits SHALL appear in both applicable groups and open the comparison associated with the selected group.

#### Scenario: Partially staged file selected
- **WHEN** a file has staged edits and additional working-tree edits
- **THEN** selecting its Changes entry SHALL open index-to-working-tree differences, and selecting its Staged Changes entry SHALL open HEAD-to-index differences.

#### Scenario: Repository contains conflicts
- **WHEN** Git reports unresolved paths
- **THEN** those paths SHALL be identified under Conflicts and the view SHALL explain applicable whole-file resolution actions without pretending to provide a merge editor.

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

### Requirement: Accessible responsive state and operation feedback
The view SHALL support keyboard repository/file and line selection, labelled actions, accessible progress/status announcements, and a usable narrow-screen layout. It SHALL distinguish loading, empty, unsupported, stale, failed, and successful states. Operation feedback SHALL identify each target repository, and a failed repository SHALL not prevent other repositories from remaining usable.

#### Scenario: Narrow viewport
- **WHEN** the available width is insufficient for sidebar and split comparison
- **THEN** the user SHALL be able to move between repository/file selection and comparison with an explicit back control and use unified diff presentation.

#### Scenario: Multi-repository operation partially fails
- **WHEN** one repository fails and another succeeds
- **THEN** feedback SHALL identify the failed and successful repositories separately and clear each finished target's busy state.

### Requirement: File selection and commit scope
The view SHALL provide independent commit-selection checkboxes for file entries and file groups, preserving them across repository switches and status refreshes while the entries remain available. Selecting a path from one area SHALL deselect its other area. Explicit modes SHALL distinguish current-repository staged changes, current-repository selected files, and selected files across repositories. The view SHALL display target counts before submission and retain failed selections/drafts. Repository checkboxes SHALL independently control bulk push.

#### Scenario: Select files across repository switches
- **WHEN** the user marks files in one repository, switches to another, marks more files, and returns
- **THEN** the original selections SHALL remain visible, and multiple-repository commit SHALL target exactly repositories containing marked files.

#### Scenario: Push selected repositories
- **WHEN** the user marks several repository checkboxes
- **THEN** a visible Push selected control SHALL identify the target count and push those repositories with independent results.

### Requirement: Collapsible repository and file groups
The view SHALL allow collapsing a repository's file list and its Changes, Staged Changes, and Conflicts groups with keyboard-accessible controls exposing expanded state. Collapse state SHALL survive status refreshes and repository switches without clearing file selections or closing the current comparison.

#### Scenario: Collapse a selected repository
- **WHEN** the user collapses the active file list and status refreshes
- **THEN** the list SHALL remain collapsed while the comparison, file selections, and draft remain available.

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
