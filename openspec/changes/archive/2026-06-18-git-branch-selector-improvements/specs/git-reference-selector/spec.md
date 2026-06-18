## ADDED Requirements

### Requirement: Remote Branch Listing
The system SHALL retrieve and list remote Git branches for a selected project.

#### Scenario: View remote branches
- **WHEN** the user opens the branch selector modal for a project and switches to the "Remote Branches" tab
- **THEN** the modal displays all remote branches (excluding the symbolic HEAD reference)

### Requirement: Git Fetch and Refresh
The system SHALL provide a Fetch button that executes a Git fetch operation synchronously on the project repository and refreshes the modal content with any newly fetched references.

#### Scenario: Synchronously fetch and refresh references
- **WHEN** the user clicks the "Fetch" button inside the branch selector modal
- **THEN** the system executes `git fetch` synchronously and reloads the modal content with the latest references without full page reload

### Requirement: Git Tag Listing
The system SHALL retrieve and list Git tags for a selected project, sorted with version-based sorting descending so that the latest tags appear first.

#### Scenario: View git tags
- **WHEN** the user selects the "Tags" tab in the branch selector modal
- **THEN** the modal lists all tags for the project

### Requirement: Reference Checkout Confirmation
The system SHALL require user confirmation through a modal dialog before performing any checkout to a local branch, remote branch, or tag.

#### Scenario: Confirm and checkout reference
- **WHEN** the user clicks on a reference (local branch, remote branch, or tag), confirms the operation on the modal dialog, and the checkout succeeds
- **THEN** the system performs the git checkout and reloads the main workspace page to reflect the new active reference
