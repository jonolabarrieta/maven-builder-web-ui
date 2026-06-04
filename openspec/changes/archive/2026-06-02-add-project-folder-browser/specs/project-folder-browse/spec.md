## ADDED Requirements

### Requirement: Workspace Path Browser
The system SHALL provide a directory browser modal to select a folder on the local filesystem during workspace creation and editing.

#### Scenario: Open directory browser for new workspace
- **WHEN** the user clicks the "Browse" button next to the "Base Directory Path" field in the new workspace modal
- **THEN** the system SHALL open a directory explorer modal initialized with the user's home directory
- **WHEN** the user navigates to a directory and clicks "Select Folder"
- **THEN** the system SHALL populate the "Base Directory Path" input field with the selected path and close the modal

#### Scenario: Open directory browser for workspace settings
- **WHEN** the user clicks the "Browse" button next to the "Base Directory Path" field in the workspace settings modal
- **THEN** the system SHALL open a directory explorer modal initialized with the current workspace base path
- **WHEN** the user navigates to a directory and clicks "Select Folder"
- **THEN** the system SHALL populate the "Base Directory Path" input field with the selected path and close the modal

### Requirement: Workspace Path Input Retention
The system SHALL allow the user to paste or manually edit the base directory path directly in the text input field, even when the directory browser is available.

#### Scenario: Manually inputting a workspace path
- **WHEN** the user types or pastes a folder path directly into the "Base Directory Path" input field
- **THEN** the text field SHALL accept the input and allow form submission with the pasted path

### Requirement: Manual Project Path Entry
The system SHALL provide an input field in the "Add Project" modal where users can type or paste any absolute path to a Maven project.

#### Scenario: Browse to a pasted project path
- **WHEN** the user enters an absolute path in the manual entry field of the "Add Project" modal and clicks the load button
- **THEN** the system SHALL refresh the file explorer view in the modal to start at that path

#### Scenario: Add project directly from pasted path
- **WHEN** the user enters an absolute path in the manual entry field and clicks the add button
- **THEN** the system SHALL add the project at that path to the workspace and refresh the workspace page
