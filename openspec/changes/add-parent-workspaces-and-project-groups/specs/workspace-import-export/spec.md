## MODIFIED Requirements

### Requirement: Export Workspace as Plain Text
The system SHALL export a workspace as a UTF-8 plain text configuration containing its name, base directory, exclusions, project kinds and order. Ungrouped legacy export SHALL retain plain Maven paths and explicit `NonMavenProject: <path>` entries. Grouped export SHALL use an explicitly versioned structured text format preserving block order, local membership, imported references, member enabled overrides and included parent definitions. Each referenced parent SHALL be embedded once using file-local identities, without database IDs or derived Maven/Git metadata. The separate paths-only export SHALL flatten effective execution order and emit each canonical absolute project path once, without metadata or type markers.

#### Scenario: User exports workspace
- **WHEN** the user triggers Export Workspace
- **THEN** the system SHALL download its configuration and ordered project entries, using the versioned format when groups or parent references are present.

#### Scenario: Export a mixed workspace
- **WHEN** the workspace contains interleaved Maven projects and explicit non-Maven folders
- **THEN** the configuration SHALL retain their kinds and relative order in either supported format.

#### Scenario: User exports only workspace paths
- **WHEN** the user triggers Export Workspace Paths on a grouped workspace
- **THEN** the downloaded file SHALL contain only absolute paths in flattened execution order, with duplicate canonical directories emitted once and no metadata, names, exclusions or type markers.

#### Scenario: Export a shared parent
- **WHEN** a consumer containing an imported parent is exported
- **THEN** the file SHALL include enough parent configuration to reconstruct that group on another installation without access to the original database.

### Requirement: Import Workspace from Plain Text
The system SHALL accept uploaded or pasted legacy workspace configurations, paths-only files and versioned grouped configurations. It SHALL create the specified workspace configuration, preserve explicit project kinds, and resolve valid ordered registrations. Unmarked legacy paths without a POM SHALL receive a warning rather than become non-Maven folders. Normalized duplicate directories SHALL NOT create duplicate registrations within one owner. Grouped imports SHALL create new parent definitions and rebuild references using file-local identities; they SHALL NOT overwrite or implicitly link existing parents by matching name or database ID. Structurally malformed files or unsupported versions SHALL be rejected before any configuration is persisted. Unavailable project directories SHALL retain the existing warning-and-skip behavior.

#### Scenario: User imports workspace successfully
- **WHEN** a valid plain text configuration is submitted
- **THEN** the system SHALL create its named workspace with base directory, exclusions and valid ordered project registrations.

#### Scenario: Round-trip a mixed workspace
- **WHEN** an export containing Maven and explicit non-Maven entries is imported
- **THEN** their kinds and relative order SHALL be preserved.

#### Scenario: Read a legacy configuration
- **WHEN** an existing configuration contains only unmarked paths including one without a POM
- **THEN** Maven paths SHALL retain their import behavior and the no-POM entry SHALL be skipped with feedback.

#### Scenario: Round-trip a grouped workspace
- **WHEN** a valid grouped configuration with local groups, ungrouped projects and an imported parent is imported
- **THEN** the new workspace SHALL preserve those boundaries, their ordered sequence and local enabled overrides and SHALL reference the newly reconstructed parent.

#### Scenario: Parent name collision
- **WHEN** an imported parent has the same name as an existing parent
- **THEN** the importer SHALL create a separate identity, report the duplicate display name and SHALL NOT modify or attach the existing parent.

#### Scenario: Unsupported or malformed grouped file
- **WHEN** a file has an unsupported version, dangling parent identity, nested group or repeated parent import within one consumer
- **THEN** the system SHALL reject the entire structural import with an actionable error and SHALL persist no new workspace, group or parent.

### Requirement: Preserve Project Execution Order
The system SHALL preserve the exact imported sequence of successfully registered projects. For grouped formats it SHALL preserve block positions and local member order, resolve imported member order from included parent definitions and produce the same effective execution sequence. Skipped unavailable registrations SHALL NOT reorder surviving entries.

#### Scenario: Preserving execution order
- **WHEN** an ungrouped configuration lists projects in a specific sequence
- **THEN** successful imports SHALL be assigned order starting from zero following that sequence.

#### Scenario: Preserving grouped execution order
- **WHEN** a grouped export represents root A, group B/C, imported D/E, root F
- **THEN** reimport SHALL preserve those boundaries and flatten to A, B, C, D, E, F for all available entries.

## ADDED Requirements

### Requirement: Recalculate grouped project metadata
Versioned grouped imports SHALL derive Maven and Git metadata from local project directories rather than import cached source data. Explicit non-Maven entries SHALL keep their classification even if a POM now exists. The importer SHALL warn about unavailable or invalid paths while preserving valid group structure and surviving entry order.

#### Scenario: Import a parent containing a folder
- **WHEN** a parent definition includes an explicit non-Maven entry whose local directory now contains a POM
- **THEN** that registration SHALL remain non-Maven and its folder name and applicable Git state SHALL be derived from disk without Maven parsing.
