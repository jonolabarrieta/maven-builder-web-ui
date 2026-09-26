## MODIFIED Requirements

### Requirement: Export Workspace as Plain Text
The system SHALL provide an option to export a workspace as a plain text file containing the workspace name, the base directory path, any excluded paths, and the project paths in their current execution order. It SHALL also provide a separate option to export only the project paths.

#### Scenario: User exports workspace
- **WHEN** the user triggers the "Export Workspace" action on the workspace details page
- **THEN** the system generates and prompts the download of a plain text file with the workspace configuration and the project paths in execution order.

#### Scenario: User exports only workspace paths
- **WHEN** the user triggers the "Export Workspace Paths" action on the workspace details page
- **THEN** the system downloads a UTF-8 plain text file containing exactly one absolute project path per line in current execution order, with no workspace metadata, project names, or exclusion entries.
