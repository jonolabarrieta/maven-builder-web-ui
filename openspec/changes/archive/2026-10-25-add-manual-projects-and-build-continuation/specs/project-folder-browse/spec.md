## MODIFIED Requirements

### Requirement: Manual Project Path Entry
The system SHALL provide an input field in the Add projects modal where users can type or paste an absolute project directory path. An Include non-Maven folders option SHALL be unchecked on each new opening and SHALL be retained during navigation within that modal. Without opt-in, addition SHALL retain Maven-only behavior. With opt-in, a selected directory without its own POM SHALL be added as exactly one folder project; a Maven directory SHALL retain existing Maven addition behavior. The same choice SHALL apply to direct-path and checkbox additions.

#### Scenario: Browse to a pasted project path
- **WHEN** the user enters an absolute path in the manual entry field and clicks the load button
- **THEN** the system SHALL refresh the explorer at that path and preserve the current non-Maven inclusion choice.

#### Scenario: Add project directly from pasted path
- **WHEN** the user enters an absolute Maven directory path and clicks Add project
- **THEN** the system SHALL add its Maven projects and refresh the workspace view.

#### Scenario: Non-Maven addition requires opt-in
- **WHEN** the user attempts to add a folder with no eligible Maven projects while non-Maven inclusion is unchecked
- **THEN** no folder registration SHALL be created and the modal SHALL explain how to enable non-Maven addition.

#### Scenario: Select non-Maven directories
- **WHEN** the user enables Include non-Maven folders and selects non-Maven directory checkboxes or adds a direct path
- **THEN** the request SHALL carry the explicit opt-in and add only the selected directories as folder registrations, without implicitly selecting descendants.

#### Scenario: Reopen the modal
- **WHEN** the Add projects modal is closed and opened again
- **THEN** Include non-Maven folders SHALL be unchecked regardless of its earlier value.
