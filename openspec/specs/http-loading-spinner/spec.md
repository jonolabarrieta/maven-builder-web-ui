# http-loading-spinner Specification

## Purpose
TBD - created by archiving change add-http-spinner. Update Purpose after archive.
## Requirements
### Requirement: Global HTTP Request Tracking
The system SHALL track all HTTP requests initiated via HTMX or vanilla JavaScript `fetch`, and standard page navigations initiated via form submissions or link clicks.

#### Scenario: HTMX Request Starts
- **WHEN** an HTMX request starts
- **THEN** the system SHALL increment the active request count and make the loading spinner visible.

#### Scenario: HTMX Request Completes
- **WHEN** an HTMX request completes (either successfully or with an error)
- **THEN** the system SHALL decrement the active request count, and hide the loading spinner if the active count is zero.

#### Scenario: Fetch Request Starts
- **WHEN** a native JavaScript `fetch` request is initiated
- **THEN** the system SHALL increment the active request count and make the loading spinner visible.

#### Scenario: Fetch Request Completes
- **WHEN** a native JavaScript `fetch` request completes (either resolves or rejects)
- **THEN** the system SHALL decrement the active request count, and hide the loading spinner if the active count is zero.

#### Scenario: Standard Link Click
- **WHEN** a link click triggers a standard page navigation
- **THEN** the system SHALL increment the active request count, make the loading spinner visible, and check the target element safely.
- **AND** the system SHALL automatically decrement the active request count after a 2-second timeout to prevent UI lockout on file downloads or cancelled transitions.

#### Scenario: Standard Form Submit
- **WHEN** a form submission triggers a standard page navigation
- **THEN** the system SHALL increment the active request count, check the target element safely, and make the loading spinner visible.
- **AND** the system SHALL automatically decrement the active request count after a 2-second timeout to prevent UI lockout on file downloads or cancelled transitions.

### Requirement: Loading Spinner UI Component
The system SHALL display a visually distinct loading spinner UI component on the screen.

#### Scenario: Spinner Visibility
- **WHEN** there is at least one active HTTP request (active count > 0)
- **THEN** the spinner component SHALL be visible.

#### Scenario: Spinner Hidden
- **WHEN** there are no active HTTP requests (active count == 0)
- **THEN** the spinner component SHALL be hidden.

