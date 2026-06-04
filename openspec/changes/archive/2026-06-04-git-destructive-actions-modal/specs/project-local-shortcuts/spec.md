## MODIFIED Requirements

### Requirement: Copy Project Path
The system SHALL display only the folder name (basename) of the Maven project's path in the workspace view and allow users to copy the project's absolute path to the clipboard by clicking it.

#### Scenario: Clicking path button copies absolute path
- **WHEN** the user clicks on the project's folder name badge/button in the table
- **THEN** the absolute path of the project is written to the user's clipboard, the button temporarily shifts to a green styling displaying "Copied!", and reverts to the original style after 2 seconds.

#### Scenario: Folder name displayed instead of full path
- **WHEN** the user views the project list in a workspace
- **THEN** each project's copy-path button displays only the last segment of the path (folder name / basename), not the full absolute path.
