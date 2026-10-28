## Purpose

Gives quick access to a repository's location and log from the Git view, and lets users export the absolute paths of repositories that have changes or were part of an operation.

## ADDED Requirements

### Requirement: Copy repository absolute path
The active repository header in the right pane SHALL offer a Copy path action that copies the repository's absolute working-tree path to the clipboard and announces the result. The repository list SHALL not offer this action.

#### Scenario: Copy path
- **WHEN** the user activates Copy path for the active repository
- **THEN** its absolute path SHALL be placed on the clipboard and a confirmation SHALL be announced

#### Scenario: Clipboard unavailable
- **WHEN** the browser refuses clipboard access
- **THEN** the view SHALL show the path in a selectable field so that the user can copy it manually

### Requirement: Open repository in file explorer
The active repository header SHALL offer Open folder, which opens the repository root in the operating system's file explorer through the server, using the existing native launcher. The target SHALL be resolved from the validated repository identity, never from a client-supplied path.

#### Scenario: Open folder
- **WHEN** the user activates Open folder
- **THEN** the system file explorer SHALL open at the repository root

#### Scenario: Explorer cannot start
- **WHEN** no file explorer can be launched
- **THEN** the view SHALL report the failure without affecting repository state

### Requirement: Download changed repository paths
The Git view SHALL offer a download of a plain-text file listing, one per line, the absolute paths of the repositories that currently have pending changes, based on the latest known status. The file name SHALL include the workspace name and the date. When no repository has changes, the control SHALL be disabled with an explanation.

#### Scenario: Download with changes
- **WHEN** two of five repositories have pending changes and the user activates the download
- **THEN** a text file SHALL be downloaded containing exactly the two absolute repository paths

#### Scenario: Nothing changed
- **WHEN** no repository has pending changes
- **THEN** the download control SHALL be disabled

### Requirement: Repository log modal
The active repository header SHALL offer View log, which opens a modal for that repository listing commits of the current branch. Each commit SHALL show its abbreviated identifier, subject, author, relative date and whether it is contained in the upstream. More commits SHALL load on demand. Selecting a commit SHALL list its changed files and SHALL open a read-only comparison for a selected file.

#### Scenario: Open the log
- **WHEN** the user activates View log for a repository
- **THEN** a modal SHALL show that repository's recent commits with unpushed commits distinguishable

#### Scenario: Load more
- **WHEN** the user scrolls to the end of the list or activates Load more
- **THEN** the next page of older commits SHALL be appended

### Requirement: Session operation history with path export
The Git view SHALL keep a history of the operations run during the page session, newest first. Each entry SHALL show the action, start time, overall state and each target repository by name with its outcome. Each entry SHALL offer a download of a text file listing the absolute paths of its target repositories. The history SHALL also offer a download of all entries, grouped by action and time. The history SHALL be cleared when the page is reloaded.

#### Scenario: Review an earlier push
- **WHEN** the user pushed three repositories and then committed in one
- **THEN** the history SHALL list the commit entry above the push entry, each with its repositories and outcomes

#### Scenario: Download an entry's paths
- **WHEN** the user activates the download on the push entry
- **THEN** a text file SHALL be downloaded with the absolute paths of the three pushed repositories

#### Scenario: Reload the page
- **WHEN** the user reloads the Git page
- **THEN** the history SHALL start empty
