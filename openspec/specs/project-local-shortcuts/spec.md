# project-local-shortcuts Specification

## Purpose
Provide local project shortcuts and path copying for Maven projects and explicitly registered folders.

## Requirements

### Requirement: Copy Project Path
The system SHALL display only the folder name (basename) of the Maven project's path in the workspace view and allow users to copy the project's absolute path to the clipboard by clicking it.

#### Scenario: Clicking path button copies absolute path
- **WHEN** the user clicks on the project's folder name badge/button in the table
- **THEN** the absolute path of the project is written to the user's clipboard, the button temporarily shifts to a green styling displaying "Copied!", and reverts to the original style after 2 seconds.

#### Scenario: Folder name displayed instead of full path
- **WHEN** the user views the project list in a workspace
- **THEN** each project's copy-path button displays only the last segment of the path (folder name / basename), not the full absolute path.

### Requirement: Local Project Shortcuts
The system SHALL provide local, per-project shortcuts for Maven and manually registered folder projects to open Visual Studio Code, reveal the directory in the native file explorer, and open a terminal rooted at the project's directory. On Windows the terminal SHALL be a visible interactive PowerShell session, preferring PowerShell 7 and falling back to Windows PowerShell. It SHALL stay open after launch and SHALL not fall back to a Command Prompt session. Paths SHALL be passed literally rather than interpreted as shell instructions. Existing Linux/macOS terminal, VS Code, and file-explorer behavior SHALL be preserved.

#### Scenario: User opens a project terminal
- **WHEN** the user activates the terminal shortcut for an available Maven or folder project
- **THEN** the system SHALL asynchronously request a terminal rooted at its absolute directory and report whether launch was accepted.

#### Scenario: Terminal shortcut on supported operating systems
- **WHEN** the user activates a terminal shortcut on Windows, macOS, or Linux
- **THEN** the system SHALL use the platform launcher while preserving paths containing spaces and special characters.

#### Scenario: PowerShell 7 is available
- **WHEN** a Windows project terminal is opened and PowerShell 7 is installed
- **THEN** a PowerShell 7 window SHALL remain open in the project directory.

#### Scenario: Only Windows PowerShell is available
- **WHEN** PowerShell 7 is unavailable and Windows PowerShell is available
- **THEN** the shortcut SHALL open Windows PowerShell in the project directory and SHALL not open cmd as the interactive shell.

#### Scenario: Unavailable directory or Windows launcher
- **WHEN** the directory is unavailable or no PowerShell launcher can be started
- **THEN** the system SHALL report an actionable launch error without executing the path as a command or silently falling back to cmd.

#### Scenario: No Linux terminal launcher is available
- **WHEN** the project terminal shortcut is activated on Linux and no supported terminal launcher can be started
- **THEN** the system SHALL report an actionable error and SHALL not execute any arbitrary shell command.
