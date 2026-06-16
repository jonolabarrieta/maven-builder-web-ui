# updater-system Specification

## Purpose
TBD - created by archiving change auto-update-system. Update Purpose after archive.
## Requirements
### Requirement: Show Current Version
The configuration page (`settings.html`) SHALL display the current version of the application using a version number defined in the application configuration.

#### Scenario: Display current version on configuration page
- **WHEN** the user navigates to the configuration page
- **THEN** the system displays the current version string next to the "Update Center" title

### Requirement: Manual Update Check
The system SHALL check for updates only when triggered by the user clicking the "Check for Updates" button. It SHALL NOT check automatically on page load or startup.

#### Scenario: User checks for updates manually
- **WHEN** the user clicks the "Check for Updates" button in the configuration UI
- **THEN** the system performs a POST request to check for new releases and updates the section with the result

### Requirement: Configurable Update URL
The update checker SHALL query a URL that is configurable via `application.properties` (e.g. `app.update.check-url`), allowing developers to mock the response locally.

#### Scenario: Update check using a mocked URL
- **WHEN** the update URL in `application.properties` is set to a local endpoint and the user triggers an update check
- **THEN** the system queries the local endpoint and displays the returned version information in the UI

### Requirement: Notification of Available Update
If the check returns a version number greater than the current version, the system SHALL display a notification containing the new version tag, release notes, and a button to initiate the update.

#### Scenario: A newer version is found
- **WHEN** the system detects a newer version than the current version during an update check
- **THEN** the configuration UI displays a notification panel with details of the new version and a "Download and Install" button

#### Scenario: The application is up to date
- **WHEN** the system detects that the latest version is less than or equal to the current version
- **THEN** the configuration UI displays a message indicating that the application is up to date

### Requirement: Execute Hot-Update and Restart
When the user confirms the update, the system SHALL download the appropriate release asset, generate a temporary platform-specific shell or batch script, execute it as a detached subprocess, and terminate the current application process.

#### Scenario: Trigger self-update download and execution
- **WHEN** the user clicks the "Download and Install" button
- **THEN** the backend downloads the new artifact, generates the updater script, starts the script in the background, and exits the application process with code 0

