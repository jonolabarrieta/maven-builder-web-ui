# workspace-import-export Specification

## Purpose
TBD - created by archiving change import-export-workspace. Update Purpose after archive.
## Requirements
### Requirement: Export Workspace as Plain Text
The system SHALL provide an option to export a workspace as a plain text file containing the workspace name, the base directory path, any excluded paths, and the project paths in their current execution order.

#### Scenario: User exports workspace
- **WHEN** the user triggers the "Export Workspace" action on the workspace details page
- **THEN** the system generates and prompts the download of a plain text file with the workspace configuration and the project paths in execution order

### Requirement: Import Workspace from Plain Text
The system SHALL allow users to import a workspace by uploading a plain text file or pasting the plain text configuration directly into a form. The imported workspace MUST be created with the specified name, base directory path, and excluded paths.

#### Scenario: User imports workspace successfully
- **WHEN** the user submits the import workspace form with valid plain text workspace configuration
- **THEN** the system creates the workspace entity and imports all listed projects on the workspace detail screen

### Requirement: Recalculate Project Metadata on Import
The system SHALL recalculate all Maven and Git project metadata dynamically upon importing a workspace. No database-stored metadata from the original workspace SHALL be imported from the text configuration.

#### Scenario: Metadata recalculation
- **WHEN** a workspace is imported
- **THEN** the system scans and parses the `pom.xml` for each project path to populate Maven metadata and resolves the current Git branch on disk

### Requirement: Preserve Project Execution Order
The system SHALL preserve the exact order of the projects as defined in the plain text configuration file when importing them into the new workspace.

#### Scenario: Preserving execution order
- **WHEN** a workspace is imported with a list of projects in a specific sequence
- **THEN** the system sets the execution order of each successfully imported project starting from 0 to match the exact sequence in the configuration file

