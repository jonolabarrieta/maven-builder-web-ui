## MODIFIED Requirements

### Requirement: Local Project Shortcuts
The system SHALL provide local, per-project shortcuts to copy the project's absolute path, open the project in Visual Studio Code, open its directory in the native file explorer, and open a terminal rooted at the project directory.

#### Scenario: User opens a project terminal
- **WHEN** the user activates the project terminal shortcut
- **THEN** the system asynchronously starts a terminal session whose working directory is the project's absolute directory and immediately reports whether launch was accepted.

#### Scenario: Terminal shortcut on supported operating systems
- **WHEN** the user activates the project terminal shortcut on Windows, macOS, or Linux
- **THEN** the system uses a platform-appropriate terminal-launch command, preserving project paths that contain spaces.

#### Scenario: No Linux terminal launcher is available
- **WHEN** the project terminal shortcut is activated on Linux and no supported terminal launcher can be started
- **THEN** the system reports an actionable error and does not execute any arbitrary shell command.
