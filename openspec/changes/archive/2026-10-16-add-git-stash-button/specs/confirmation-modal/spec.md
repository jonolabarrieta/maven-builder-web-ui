## ADDED Requirements

### Requirement: Optional Checkbox Option in Confirmation Dialog
The reusable confirmation dialog SHALL support an optional single checkbox option configured with a label and an initial checked state. When the option is present, the dialog SHALL render one native checkbox below its message, and the confirm callback SHALL receive the checkbox's current checked state. Dialogs that do not configure the option SHALL behave exactly as before, with no checkbox rendered.

#### Scenario: Dialog with checkbox option opens
- **WHEN** the dialog is opened with a checkbox option (label and initial checked state)
- **THEN** the dialog shows a single checkbox with the given label, set to the configured initial state, and keyboard focus handling continues to cover the added control.

#### Scenario: Confirm passes the checked state
- **WHEN** the user toggles the checkbox and activates the confirm action
- **THEN** the confirm callback receives the checkbox's final checked state as a boolean.

#### Scenario: Initial checked state respected
- **WHEN** the dialog is opened with the checkbox option initially checked
- **THEN** the checkbox renders checked and confirming without interaction passes `true`.

#### Scenario: Dialog without checkbox option unchanged
- **WHEN** the dialog is opened without a checkbox option
- **THEN** no checkbox is rendered and confirming invokes the callback exactly as it does today.
