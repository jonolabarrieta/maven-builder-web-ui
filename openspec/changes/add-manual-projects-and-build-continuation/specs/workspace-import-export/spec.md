## MODIFIED Requirements

### Requirement: Export Workspace as Plain Text
The system SHALL export the workspace name, base directory, exclusions, and registered project paths in their current order as plain text. Full configuration export SHALL retain legacy plain path entries for Maven projects and encode explicit non-Maven entries as `NonMavenProject: <path>` in the same ordered sequence. The separate export-only-paths action SHALL remain a UTF-8 file with exactly one absolute path per registered project and no type or workspace metadata.

#### Scenario: User exports workspace
- **WHEN** the user triggers Export Workspace
- **THEN** the system SHALL download the workspace configuration and ordered project entries.

#### Scenario: Export a mixed workspace
- **WHEN** the exported workspace includes explicitly registered folders
- **THEN** the configuration SHALL retain their classification and their order relative to Maven entries.

#### Scenario: User exports only workspace paths
- **WHEN** the user triggers Export Workspace Paths
- **THEN** the downloaded file SHALL contain exactly one absolute path per registration in current order, without metadata, names, exclusion entries, or type markers.

### Requirement: Import Workspace from Plain Text
The system SHALL support uploading or pasting a plain text workspace configuration and create its specified name, base directory, exclusions, and ordered project registrations. It SHALL read existing Maven-only configurations unchanged and accept explicit `NonMavenProject:` entries as manual folder registrations. It SHALL not infer new folder registrations from unmarked paths without a POM. Invalid/unavailable entries SHALL receive feedback, and normalized duplicate directories SHALL not create duplicate registrations.

#### Scenario: User imports workspace successfully
- **WHEN** a valid workspace configuration is submitted
- **THEN** the system SHALL create the workspace and import its valid listed projects in their specified order.

#### Scenario: Round-trip a mixed workspace
- **WHEN** a full export containing interleaved Maven and NonMavenProject entries is imported
- **THEN** the system SHALL preserve the entry kinds and ordered sequence.

#### Scenario: Read a legacy configuration
- **WHEN** an existing configuration contains only unmarked project paths, including one without a POM
- **THEN** Maven paths SHALL retain their existing import behavior and the no-POM path SHALL be skipped with feedback rather than automatically becoming a folder registration.

### Requirement: Recalculate Project Metadata on Import
The system SHALL recalculate project metadata from local files during import and SHALL not import database-derived metadata. Maven entries SHALL parse their POM and resolve Git information; explicit non-Maven entries SHALL derive their display name and Git information from their directory without requiring a POM or fabricating Maven coordinates.

#### Scenario: Metadata recalculation
- **WHEN** a Maven entry is imported
- **THEN** its Maven metadata SHALL be populated from its local POM and its current Git branch SHALL be resolved from disk.

#### Scenario: Import a folder entry
- **WHEN** an explicit non-Maven entry is imported
- **THEN** the folder basename and applicable current Git metadata SHALL be derived without Maven parsing, even if a POM has appeared since export.
