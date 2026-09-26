## MODIFIED Requirements

### Requirement: Recursive Version Resolution
The system SHALL resolve Maven properties recursively for Maven project properties (groupId, artifactId, and version).
The system SHALL inherit and resolve custom properties (such as `${revision}`) from parent and grandparent POM files.
When the active build profile specifies a displayed-version Maven profile ID, the system SHALL include matching profile properties from the project and its reachable parent POMs when resolving displayed versions, with selected-profile properties overriding base properties at their corresponding level.
The system SHALL resolve chained property expressions and standard Maven property expressions like `${project.parent.version}`, `${parent.version}`, `${project.groupId}`, and `${project.version}` without infinite recursion.

#### Scenario: Version resolution from parent POM property
- **WHEN** a workspace is scanned or a project is imported and no displayed-version Maven profile is configured
- **THEN** the system resolves the project version by substituting custom property placeholders (like `${revision}`) defined in its parent or grandparent POM files.

#### Scenario: Version from a parent Maven profile
- **WHEN** the selected Maven profile is `batsdlc`, a parent POM declares `<version>${revision}</version>`, and its `batsdlc` profile defines `revision` as `${r01f.version}` with `r01f.version` equal to `0.3.20-SNAPSHOT`
- **THEN** the parent and children inheriting that version display `0.3.20-SNAPSHOT`.

#### Scenario: Standard Maven property resolution
- **WHEN** a workspace is scanned or a project is imported and its version refers to `${project.parent.version}` or `${parent.version}`
- **THEN** the system resolves it to the version of the parent project under the selected displayed-version profile, when one is configured.

#### Scenario: Cyclic profile property expression
- **WHEN** a selected Maven profile's version properties reference each other cyclically
- **THEN** version resolution terminates and does not replace the displayed version with an invalid partial value.
