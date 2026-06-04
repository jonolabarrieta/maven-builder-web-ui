## ADDED Requirements

### Requirement: Global Configuration Screen
The system SHALL provide a global settings and configuration screen accessible via the main navigation bar.

#### Scenario: Navigate to global settings
- **WHEN** the user clicks the "Configuration" link in the navigation bar
- **THEN** the system SHALL render the configuration management screen displaying sections for Favorite Path and Java Installations.

### Requirement: Favorite Path Configuration
The system SHALL allow the user to save a favorite directory path, which SHALL be used to prepopulate directory search inputs or file explorer views when creating workspaces or adding projects.

#### Scenario: Save favorite path
- **WHEN** the user inputs a valid filesystem path in the favorite path field and submits
- **THEN** the system SHALL save the favorite path and show it as saved on the settings screen.

#### Scenario: Use favorite path in modal
- **WHEN** the user opens the "New Workspace" modal
- **THEN** the base directory path input field SHALL be prepopulated with the configured favorite path.

#### Scenario: Use favorite path as starting path in file explorer for new workspace
- **WHEN** the user opens the "New Workspace" modal and clicks the "Browse" directory button
- **THEN** the system SHALL open a file explorer modal starting at the configured favorite directory path.

### Requirement: Add Java Installation
The system SHALL allow the user to register multiple Java installations by specifying a name and its corresponding `JAVA_HOME` directory path.

#### Scenario: Add new Java installation
- **WHEN** the user inputs a name and a valid directory path and clicks "Add"
- **THEN** the system SHALL register the installation, save it in the database, and display it in the list of Java versions.

### Requirement: Select Active Java Version
The system SHALL allow the user to mark a specific registered Java version as the default active version.

#### Scenario: Select default active Java version
- **WHEN** the user marks a registered Java version as the default active version
- **THEN** the system SHALL store it as the active version.

### Requirement: Run Build with Selected Java Version
The system SHALL inject the configured `JAVA_HOME` of the selected Java version into the environment variables when executing Maven build processes.

#### Scenario: Build project using configured Java version
- **WHEN** a user triggers a project build and there is a configured default active Java version
- **THEN** the process execution service SHALL execute the build command with `JAVA_HOME` set to the configured home path in its environment variables, and prepend its `bin` directory to the `PATH` environment variable.
