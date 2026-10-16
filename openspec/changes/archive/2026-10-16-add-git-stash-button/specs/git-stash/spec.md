## ADDED Requirements

### Requirement: Bulk Stash Action in Git Changes Menu
The system SHALL provide a **Stash selected** action in the bulk "Git changes" menu of the workspace detail page. The action SHALL apply to the currently selected projects, excluding projects marked as Skipped, and SHALL require confirmation through the reusable application dialog before running.

#### Scenario: Stash entry visible in bulk menu
- **WHEN** the user selects one or more projects and opens the "Git changes" menu
- **THEN** the menu shows a "Stash selected" entry alongside "Unstage selected" and "Discard changes".

#### Scenario: Opening stash shows confirmation dialog with untracked option
- **WHEN** the user activates "Stash selected" with at least one project selected
- **THEN** the reusable confirmation dialog opens with a title and message describing the stash of the selected projects, and a single checkbox labeled to include untracked files that is unchecked by default.

#### Scenario: Cancelling stash runs nothing
- **WHEN** the user cancels, presses Escape, or dismisses the stash dialog
- **THEN** no stash request is sent and no project working tree is changed.

#### Scenario: No projects selected
- **WHEN** the user attempts a stash action with no projects selected
- **THEN** the system shows an informational dialog stating that at least one project must be selected and does not send a request.

### Requirement: Untracked File Choice Controls the Stash Command
The stash request SHALL carry the user's choice about untracked files. The backend SHALL run `git stash push -u` in each selected project when untracked files are included, and `git stash push` when they are not.

#### Scenario: Stash excluding untracked files
- **WHEN** the user confirms the stash dialog with the untracked option unchecked
- **THEN** the system sends `POST /workspaces/{id}/bulk-action` with action `stash` and `includeUntracked=false`, and each selected enabled project runs `git stash push`.

#### Scenario: Stash including untracked files
- **WHEN** the user confirms the stash dialog with the untracked option checked
- **THEN** the system sends `POST /workspaces/{id}/bulk-action` with action `stash` and `includeUntracked=true`, and each selected enabled project runs `git stash push -u`.

#### Scenario: Only selected projects are stashed
- **WHEN** the bulk stash request includes project IDs and some projects are marked Skipped
- **THEN** the stash command runs only on the selected projects that are enabled.

### Requirement: Stash Outcome Reported in Build Output
The system SHALL broadcast a `git-stash` action summary after a bulk stash and SHALL reflect per-project failures.

#### Scenario: Successful stash summary
- **WHEN** every selected project stashes successfully
- **THEN** the build output shows a "Git Stash" result indicating success for the operation.

#### Scenario: Partial or total failure summary
- **WHEN** one or more selected projects fail to stash (for example, a repository without an initial commit)
- **THEN** the `git-stash` summary reports the operation as failed and lists the artifact IDs of the projects that failed.
