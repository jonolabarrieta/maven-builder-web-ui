# workspace-run-configurations Specification

## Purpose
Lets each workspace define the commands that start its services or tools, and lets the user choose which of them to launch at a given moment.

## Requirements
### Requirement: Workspace RUN entries
The system SHALL let the user create, edit, duplicate, reorder and delete RUN entries for a non-parent workspace. Each entry SHALL have:

- a name, which is required and unique within the workspace
- an optional project, which may be one of the workspace's own projects (Maven or folder) or a member of one of its imported groups
- a working directory
- a command, which is required
- optional environment variables, as ordered name/value pairs
- an option to use the workspace's Java installation

When a project is selected, the working directory SHALL default to that project's directory and SHALL remain editable. When no project is selected, a working directory SHALL be required. A project MAY have any number of entries. Entries SHALL persist across restarts. Each workspace SHALL own its entries, and a consumer SHALL NOT inherit a parent workspace's entries.

#### Scenario: Create an entry for a project
- **WHEN** the user adds an entry named "api", selects project `api-service` and enters `mvn spring-boot:run`
- **THEN** the entry SHALL be saved with `api-service`'s directory as its working directory and SHALL appear in the workspace's RUN list.

#### Scenario: Several entries for one project
- **WHEN** the user adds the entries "front dev" (`npm run dev`) and "front mock" (`npm run mock`), both for project `front`
- **THEN** both entries SHALL be stored and listed independently.

#### Scenario: Entry without a project
- **WHEN** the user adds an entry with no project, the directory `/opt/infra` and the command `docker compose up`
- **THEN** the entry SHALL be saved and SHALL run in `/opt/infra`.

#### Scenario: Invalid entry
- **WHEN** the user saves an entry with an empty command, a duplicate name, or no project and no working directory
- **THEN** the system SHALL reject the save with field-level feedback and SHALL persist nothing.

#### Scenario: Linked project removed
- **WHEN** the project referenced by an entry is removed from the workspace
- **THEN** the entry SHALL be kept with its last working directory, SHALL be shown as unlinked, and SHALL remain launchable if that directory exists.

### Requirement: Shell command execution
The system SHALL run an entry's command through the platform shell (`sh -c` on Linux and macOS, `cmd /c` on Windows) in the entry's working directory, so that pipes, `&&` and variable expansion behave as they do in a terminal. Configured environment variables SHALL be added to the inherited environment. When the entry uses the workspace's Java installation and the workspace has one, JAVA_HOME SHALL be set and its `bin` directory SHALL be placed first on PATH.

#### Scenario: Environment and Java applied
- **WHEN** an entry with `SERVER_PORT=8081` that uses the workspace's Java installation is started in a workspace configured with JDK 21
- **THEN** the process SHALL see `SERVER_PORT=8081`, JAVA_HOME pointing to JDK 21, and that JDK's `bin` directory first on PATH.

#### Scenario: Missing working directory
- **WHEN** an entry is started and its working directory does not exist
- **THEN** no process SHALL start and the user SHALL see an error naming the entry and the missing directory.

### Requirement: Launch selection modal
The workspace page SHALL show a RUN action. The action SHALL open a modal listing the workspace's entries in their configured order, with a checkbox for each entry. The modal SHALL preselect the entries the user started the last time from that workspace. If no previous selection exists, it SHALL preselect none. Entries that are already running SHALL be shown with their state and SHALL NOT be selectable. Confirming the modal SHALL start every selected entry at the same time, without waiting for any of them to become ready. The confirmed selection SHALL be remembered for that workspace.

#### Scenario: Start a chosen subset
- **WHEN** the workspace has the entries "api", "worker" and "front", and the user selects "api" and "front" and confirms
- **THEN** "api" and "front" SHALL start, "worker" SHALL NOT start, and the next time the modal opens "api" and "front" SHALL be preselected.

#### Scenario: Entry already running
- **WHEN** the modal is opened while "api" is running
- **THEN** "api" SHALL be shown as running with its checkbox disabled, and confirming SHALL NOT start a second "api".

#### Scenario: No entries configured
- **WHEN** the user activates RUN in a workspace with no entries
- **THEN** the modal SHALL explain that no RUN entries exist and SHALL offer a link to the RUN configuration.

#### Scenario: Nothing selected
- **WHEN** the user confirms the modal with no entry selected
- **THEN** no process SHALL start and the confirm action SHALL be unavailable.
