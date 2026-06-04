## ADDED Requirements

### Requirement: Copy Project Path
The system SHALL display the relative path of the Maven project in the workspace view and allow users to copy the project's absolute path to the clipboard by clicking it.

#### Scenario: Clicking path button copies absolute path
- **WHEN** the user clicks on the project's relative path badge/button in the table
- **THEN** the absolute path of the project is written to the user's clipboard, the button temporarily shifts to a green styling displaying "Copied!", and reverts to the original style after 2 seconds.

### Requirement: Open Project in VSCode
The system SHALL provide a button for each project that launches Visual Studio Code (VSCode) targeting that project's directory.

#### Scenario: Open project in VSCode
- **WHEN** the user clicks the "Open in VSCode" action button for a project
- **THEN** the backend executes the system command to launch VSCode targeting the project's absolute path.

### Requirement: Open Project in File Explorer
The system SHALL provide a button for each project that opens the native operating system file explorer at the project's directory.

#### Scenario: Open project in Windows Explorer
- **WHEN** the user clicks the "Open in File Explorer" button, and the host operating system is Windows
- **THEN** the backend launches "explorer.exe" targeting the project's absolute path.

#### Scenario: Open project in macOS Finder
- **WHEN** the user clicks the "Open in File Explorer" button, and the host operating system is macOS
- **THEN** the backend launches the "open" command targeting the project's absolute path.

#### Scenario: Open project in Linux File Manager
- **WHEN** the user clicks the "Open in File Explorer" button, and the host operating system is Linux
- **THEN** the backend launches the "xdg-open" command targeting the project's absolute path.
