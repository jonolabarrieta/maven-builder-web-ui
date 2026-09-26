# recursive-modules-display Specification

## Purpose
TBD - created by archiving change fix-recursive-modules-and-versions. Update Purpose after archive.
## Requirements
### Requirement: Recursive Module Exploration
The system SHALL dynamically verify if a Maven project contains physical submodules within the workspace database.
The system SHALL render "View Recursive" and "Dive" buttons only when a project contains physical submodules.
The system SHALL load and display direct child modules recursively in the module navigation modal.

#### Scenario: View Recursive button visibility
- **WHEN** the user views the project list and a project has physical child submodules in the workspace database
- **THEN** the "View Recursive" button is shown for that project, and if it has no physical children, the text "No submodules" is shown instead.

#### Scenario: Dive into child modules
- **WHEN** the user clicks "View Recursive" on a project or "Dive" on a submodule inside the children modal
- **THEN** the children modal displays only the direct physical children of that project, showing "Dive" buttons for children that also have submodules.

### Requirement: Recursive Version Resolution
The system SHALL resolve Maven properties recursively for the Maven project properties (groupId, artifactId, and version).
The system SHALL inherit and resolve custom properties (such as `${revision}`) from parent and grandparent POM files.
The system SHALL resolve standard Maven property expressions like `${project.parent.version}`, `${parent.version}`, `${project.groupId}`, and `${project.version}`.
When the active build profile specifies a displayed-version Maven profile ID, the system SHALL include matching profile properties from the project and its reachable parent POMs when resolving displayed versions, with selected-profile properties overriding base properties at their corresponding level.
The system SHALL resolve chained property expressions without infinite recursion.

#### Scenario: Version resolution from parent POM property
- **WHEN** a workspace is scanned or a project is imported
- **THEN** the system resolves the project version by substituting custom property placeholders (like `${revision}`) defined in its parent or grandparent POM files.

#### Scenario: Standard Maven property resolution
- **WHEN** a workspace is scanned or a project is imported and its version refers to `${project.parent.version}` or `${parent.version}`
- **THEN** the system resolves it to the version of the parent project.

#### Scenario: Version from a parent Maven profile
- **WHEN** the selected Maven profile is `batsdlc`, a parent POM declares `<version>${revision}</version>`, and its `batsdlc` profile defines `revision` as `${r01f.version}` with `r01f.version` equal to `0.3.20-SNAPSHOT`
- **THEN** the parent and children inheriting that version display `0.3.20-SNAPSHOT`.

#### Scenario: Cyclic profile property expression
- **WHEN** a selected Maven profile's version properties reference each other cyclically
- **THEN** version resolution terminates and does not replace the displayed version with an invalid partial value.
