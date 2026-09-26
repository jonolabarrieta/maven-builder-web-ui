## ADDED Requirements

### Requirement: Download Current Build Log
The system SHALL let the user download the build output currently captured in the workspace page as a UTF-8 plain-text file without persisting it server-side. The file SHALL preserve all captured tab labels and lines, not only the tab currently visible.

#### Scenario: User downloads the current build session
- **WHEN** the user selects the build-log download action after one or more log lines have been received
- **THEN** the browser downloads a `.txt` file containing a workspace/timestamp header and all captured log tabs and lines in their displayed order.

#### Scenario: No build output is available
- **WHEN** the user selects the build-log download action before any log line has been captured
- **THEN** the system does not create an empty misleading download and informs the user that there is no log output to download.

### Requirement: Build an Inclusive Range from a Module
The system SHALL provide a module-level build selector with actions to build only the selected module, from the selected module through the last project, or from the first project through the selected module. The selected range SHALL follow the workspace execution order and include the selected module.

#### Scenario: Build from the selected module
- **WHEN** the user selects **Build from here** for a module
- **THEN** the system sequentially builds that module and every later project in workspace execution order, subject to the existing disabled-project and failure behavior.

#### Scenario: Build through the selected module
- **WHEN** the user selects **Build through here** for a module
- **THEN** the system sequentially builds every project from the first one through the selected module in workspace execution order.

#### Scenario: Build only the selected module
- **WHEN** the user selects **Only this module** for a module
- **THEN** the system builds only that module using the active build profile and workspace Java configuration.

#### Scenario: Project does not belong to the workspace
- **WHEN** a range-build request names a project outside the requested workspace
- **THEN** the system rejects the request and does not start any build.
