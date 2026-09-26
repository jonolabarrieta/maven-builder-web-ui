## MODIFIED Requirements

### Requirement: Copy Project Path
The system SHALL display only the folder name (basename) of the Maven project's path in the workspace view and allow users to copy the project's absolute path to the clipboard by clicking it.

#### Scenario: Clicking path button copies absolute path
- **WHEN** the user clicks on the project's folder name badge/button in the table
- **THEN** the absolute path of the project is written to the user's clipboard, the button temporarily shifts to a green styling displaying "Copied!", and reverts to the original style after 2 seconds.

#### Scenario: Folder name displayed instead of full path
- **WHEN** the user views the project list in a workspace
- **THEN** each project's copy-path button displays only the last segment of the path (folder name / basename), not the full absolute path.

### Requirement: Local Project Shortcuts
The system SHALL provide local, per-project shortcuts to open the project in Visual Studio Code, open its directory in the native file explorer, and open a terminal rooted at the project directory.

#### Scenario: User opens a project terminal
- **WHEN** the user activates the project terminal shortcut
- **THEN** the system asynchronously starts a terminal session whose working directory is the project's absolute directory and immediately reports whether launch was accepted.

#### Scenario: Terminal shortcut on supported operating systems
- **WHEN** the user activates the project terminal shortcut on Windows, macOS, or Linux
- **THEN** the system uses a platform-appropriate terminal-launch command, preserving project paths that contain spaces.

#### Scenario: No Linux terminal launcher is available
- **WHEN** the project terminal shortcut is activated on Linux and no supported terminal launcher can be started
- **THEN** the system reports an actionable error and does not execute any arbitrary shell command.
