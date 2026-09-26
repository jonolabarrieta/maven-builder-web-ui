# Spec Delta

## MODIFIED Requirements

### Requirement: Global HTTP Request Tracking
The system SHALL track supported HTTP requests and page transitions from every top-level application page, including requests initiated through HTMX, native JavaScript `fetch`, standard form submissions, and same-origin link navigation.

#### Scenario: HTMX Request Starts
- **WHEN** a non-excluded HTMX request starts
- **THEN** the system SHALL register the request as active and make the loading spinner visible

#### Scenario: HTMX Request Completes
- **WHEN** a tracked HTMX request succeeds, fails, or is aborted
- **THEN** the system SHALL release that request and hide the loading spinner only when no tracked request remains active

#### Scenario: Fetch Request Starts
- **WHEN** a native JavaScript `fetch` request is initiated
- **THEN** the system SHALL register the request as active and make the loading spinner visible

#### Scenario: Fetch Request Completes
- **WHEN** a tracked `fetch` request resolves or rejects
- **THEN** the system SHALL release that request and hide the loading spinner only when no tracked request remains active

#### Scenario: Standard Link Click
- **WHEN** an unmodified link click triggers a standard same-origin page navigation in the current browsing context
- **THEN** the system SHALL make the loading spinner visible for the transition
- **AND** the system SHALL clear the transition state after a bounded fallback if the page does not unload

#### Scenario: Standard Form Submit
- **WHEN** a non-cancelled, non-HTMX form submission triggers a standard page navigation
- **THEN** the system SHALL make the loading spinner visible for the transition
- **AND** the system SHALL clear the transition state after a bounded fallback if the page does not unload

#### Scenario: Concurrent requests overlap
- **WHEN** two or more tracked requests overlap
- **THEN** completing one request SHALL NOT hide the spinner while another tracked request remains active

#### Scenario: Explicitly excluded request
- **WHEN** an interaction is explicitly marked to use its own contextual progress indicator or to continue as non-blocking background activity
- **THEN** the global tracker SHALL NOT show or retain the global spinner for that interaction

### Requirement: Loading Spinner UI Component
The system SHALL display one shared, visually distinct and accessible loading overlay whenever global request tracking reports active work.

#### Scenario: Spinner Visibility
- **WHEN** the first tracked request or page transition becomes active
- **THEN** the spinner overlay SHALL become visible and communicate a waiting status to assistive technology

#### Scenario: Spinner Hidden
- **WHEN** no tracked request or page transition remains active
- **THEN** the spinner overlay SHALL be hidden and SHALL no longer announce a waiting state

#### Scenario: Repeated interactions during a wait
- **WHEN** the global spinner overlay is visible
- **THEN** the interface SHALL prevent accidental duplicate activation of the obscured controls

#### Scenario: Page restored from browser history
- **WHEN** a page is restored from the browser back-forward cache
- **THEN** the loading overlay SHALL be reset to a hidden state unless new tracked work starts
