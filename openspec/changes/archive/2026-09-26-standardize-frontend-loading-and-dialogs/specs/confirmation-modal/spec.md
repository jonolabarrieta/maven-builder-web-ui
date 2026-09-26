# Spec Delta

## ADDED Requirements

### Requirement: Application-Owned Dialog Coverage
The system SHALL use application-rendered HTML/CSS dialogs for user acknowledgement and confirmation throughout the frontend and SHALL NOT invoke native browser `alert()`, `confirm()`, or `prompt()` dialogs.

#### Scenario: Validation message requires acknowledgement
- **WHEN** a user action cannot proceed because required input or selection is missing
- **THEN** the system SHALL present an informational application dialog with a single acknowledgement action

#### Scenario: Request fails
- **WHEN** an asynchronous user action fails or returns a non-success response
- **THEN** the system SHALL present an application error dialog with an actionable, non-empty message

#### Scenario: Action requires confirmation
- **WHEN** an action is destructive or otherwise requires explicit confirmation
- **THEN** the system SHALL present an application confirmation dialog with distinct confirm and cancel actions

### Requirement: Dialog Interaction Modes
The reusable dialog SHALL support informational, error, standard-confirmation, and typed-confirmation modes while preserving the Indigo/glass visual language of the application.

#### Scenario: Informational or error dialog
- **WHEN** an informational or error dialog opens
- **THEN** the dialog SHALL show its title, message, visual severity, and one acknowledgement action without a misleading cancel action

#### Scenario: Standard confirmation accepted
- **WHEN** the user activates the confirm action in a standard-confirmation dialog
- **THEN** the dialog SHALL close and the configured operation SHALL run exactly once

#### Scenario: Confirmation cancelled
- **WHEN** the user activates cancel, presses Escape, or dismisses a cancelable confirmation through its backdrop
- **THEN** the dialog SHALL close without running the configured operation

#### Scenario: Typed confirmation opened
- **WHEN** a typed-confirmation dialog opens with required text
- **THEN** its confirm action SHALL remain disabled until the entered text exactly matches the required text

#### Scenario: Asynchronous failure has contextual loading UI
- **WHEN** an operation fails while a contextual loading indicator is active
- **THEN** the contextual loading indicator SHALL be cleared before the error dialog becomes interactive
- **AND** the underlying form or controls SHALL return to a usable state

### Requirement: Accessible and Safe Dialog Behavior
Every application dialog SHALL expose modal semantics, manage keyboard focus, and render externally derived message content as text rather than executable markup.

#### Scenario: Dialog opens
- **WHEN** a dialog becomes visible
- **THEN** focus SHALL move inside the dialog, keyboard focus SHALL remain within it, and its title and description SHALL be programmatically associated with the modal

#### Scenario: Dialog closes
- **WHEN** a dialog closes by any supported action
- **THEN** focus SHALL return to the control that opened it when that control still exists

#### Scenario: Dynamic message is rendered
- **WHEN** a dialog message includes a workspace name, project name, path, branch, server response, or network error
- **THEN** the dynamic value SHALL be displayed as text and SHALL NOT be interpreted as HTML

#### Scenario: Multiple dialog requests occur
- **WHEN** code requests a new dialog while another application dialog is open
- **THEN** the component SHALL resolve the current dialog deterministically before presenting another, without stacking inaccessible overlays or callbacks

