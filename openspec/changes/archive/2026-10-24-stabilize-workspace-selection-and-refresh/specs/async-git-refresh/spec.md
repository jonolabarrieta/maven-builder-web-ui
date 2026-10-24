## MODIFIED Requirements

### Requirement: Asynchronous Git Status Refresh

The system SHALL render the workspace detail page promptly with cached Git branch names, then request current Git status in the background and update only the displayed branch indicators and their contextual status. The background update SHALL NOT replace project rows, checkboxes, or sorting controls, and SHALL NOT show a blocking full-screen spinner during normal page load.

#### Scenario: Background refresh on page load
- **WHEN** the user opens a workspace detail page
- **THEN** the project list SHALL be usable immediately with cached branch names
- **AND** one background request SHALL start to obtain current branch names.

#### Scenario: Current branches arrive
- **WHEN** the background Git request completes successfully
- **THEN** each returned branch SHALL update only its matching project's branch indicator
- **AND** the existing project rows and their interactive controls SHALL remain in place
- **AND** the contextual Git status SHALL indicate completion.

#### Scenario: Git lookup fails
- **WHEN** the background Git request fails or is aborted without a replacement request
- **THEN** cached branch names SHALL remain visible
- **AND** the contextual Git status SHALL indicate that branch information could not be refreshed.

#### Scenario: Explicit refresh supersedes an older Git response
- **WHEN** an explicit workspace Refresh starts while the on-load Git request is pending
- **THEN** the older response SHALL NOT overwrite the result of the newer refresh.
