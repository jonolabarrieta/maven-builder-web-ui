# notification-history Specification

## Purpose
TBD - created by archiving change add-notification-history. Update Purpose after archive.
## Requirements
### Requirement: Persisted Notification History
The system SHALL persist every user-facing notification in the database so it remains available after the transient toast is dismissed, the page is reloaded, or the application restarts. Notifications SHALL be recorded for build and Git action summaries, for build-failure notices, and for client-side local success/error messages. Each notification SHALL record its category, action, success or severity, title, message, workspace, affected project artifact IDs, creation timestamp, and read state.

#### Scenario: Action summary is recorded
- **WHEN** a build or Git action summary is published to the client
- **THEN** the system SHALL persist a notification capturing the action, success state, and the failed, succeeded, changed, and no-changes project lists before delivering it.

#### Scenario: Build failure is recorded
- **WHEN** a build failure notice is published with a failed project and the remaining project IDs
- **THEN** the system SHALL persist a notification identifying the failure and offering the same context as the retry prompt.

#### Scenario: Local message is recorded
- **WHEN** the page shows a local success or error message for an action
- **THEN** the system SHALL send that message to the notification endpoint and persist it, without delaying or blocking the on-screen message if the request fails.

#### Scenario: History survives reload and restart
- **WHEN** the user reloads the page or restarts the application after notifications were recorded
- **THEN** the previously recorded notifications SHALL still be listed in the notification history.

### Requirement: Notification History Panel and Unread Badge
The system SHALL present a notification history control in the workspace detail navigation with a badge showing the number of unread notifications. Activating the control SHALL open a panel listing notifications newest first, showing for each its type/title, message, affected projects, and timestamp.

#### Scenario: Badge reflects unread count
- **WHEN** the workspace detail page is loaded and unread notifications exist
- **THEN** the navigation control SHALL display a badge with the unread notification count, and the badge SHALL be hidden when the count is zero.

#### Scenario: Opening the panel lists history
- **WHEN** the user activates the notification control
- **THEN** the panel SHALL open and list the stored notifications newest first with their type, message, projects, and timestamp.

#### Scenario: Opening marks notifications read
- **WHEN** the user opens the notification panel
- **THEN** all current notifications SHALL be marked as read and the unread badge SHALL be cleared.

#### Scenario: Live update without reload
- **WHEN** a new notification arrives while the workspace detail page is open
- **THEN** the history panel SHALL include it and the unread badge SHALL increase when the panel is closed, without a full page reload.

### Requirement: Notification Retention and Clearing
The system SHALL bound the stored history to a fixed maximum number of most recent notifications and SHALL allow the user to clear the entire history.

#### Scenario: History is bounded
- **WHEN** recording a notification would exceed the maximum stored history
- **THEN** the system SHALL discard the oldest notifications so that no more than the maximum are retained.

#### Scenario: Clear all notifications
- **WHEN** the user activates "Clear all" in the notification panel
- **THEN** the system SHALL delete the stored notification history, the panel SHALL show an empty state, and the unread badge SHALL be hidden.

### Requirement: Safe Rendering of Notification Content
Notification content that originates from project names, branches, paths, or server errors SHALL be displayed as text and SHALL NOT be interpreted as executable markup.

#### Scenario: Untrusted value in a notification
- **WHEN** a notification message or project name contains markup characters
- **THEN** the panel SHALL display the value as literal text and SHALL NOT create the corresponding elements.

