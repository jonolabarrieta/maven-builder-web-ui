# manual-workspace-projects Specification

## Purpose
Support explicit non-Maven folder registration alongside Maven projects while preserving identity, capabilities, hierarchy, and execution order.

## Requirements

### Requirement: Explicit non-Maven registration
The system SHALL register a selected existing folder without a POM only through explicit non-Maven opt-in. Workspace creation and automatic scans SHALL continue adding only Maven projects. A selected non-Maven folder SHALL create one registration, without recursively registering its descendants. Registrations SHALL be deduplicated by canonical directory identity within the workspace and remain removable without deleting files.

#### Scenario: Create a mixed workspace
- **WHEN** a workspace is created beneath a folder containing Maven and non-Maven projects
- **THEN** only Maven projects SHALL be registered automatically.

#### Scenario: Explicitly add a non-Maven folder
- **WHEN** the user enables non-Maven inclusion and selects an existing folder without its own POM
- **THEN** one folder registration SHALL be created with a stable ID and the folder basename as its display name.

#### Scenario: Duplicate or invalid addition
- **WHEN** the user repeats a canonical folder path, selects an ordinary file, or supplies an unavailable directory
- **THEN** a duplicate SHALL retain its existing identity and order, while invalid additions SHALL report an error without creating a registration or claiming success.

### Requirement: Durable folder identity and classification
The system SHALL persist an explicit Maven or folder kind for registrations, treating existing registrations as Maven during migration. Explicit folders SHALL retain their identity, kind, order, enabled state, and Git/local actions across refresh and restart. A missing directory SHALL remain identifiable as unavailable. Refresh SHALL not implicitly promote a folder when a POM appears or demote a Maven project when its POM disappears.

#### Scenario: Refresh an explicitly added folder
- **WHEN** a workspace containing a manually registered non-Maven folder is refreshed
- **THEN** the folder SHALL retain its ID, kind, position, and state without requiring POM parsing or disappearing from the list.

#### Scenario: Change directory availability or POM presence
- **WHEN** a registered folder becomes unavailable or gains a POM
- **THEN** its registration SHALL remain a folder, with unavailable actions explained where necessary; explicit Maven addition at the same path SHALL be required for promotion and SHALL preserve its identity.

### Requirement: Folder capabilities and Maven execution boundaries
The system SHALL label folder entries distinctly, display their basename without fabricated Maven coordinates, and support path copying, local shortcuts, selection, ordering, removal, and applicable Git actions. Maven-only controls SHALL be unavailable for folders, and server-side build/POM operations SHALL enforce the same boundary. Mixed builds SHALL execute eligible Maven entries in order and identify excluded folders; folder-only requests SHALL start no Maven process and report that no Maven projects are eligible.

#### Scenario: Manage a folder registration
- **WHEN** the user views a registered non-Maven folder
- **THEN** the view SHALL expose its type and supported Git/local actions without Maven coordinates, POM property controls, or a misleading build action.

#### Scenario: Request a mixed or folder-only build
- **WHEN** a build request contains Maven and folder registrations, or only folders
- **THEN** folders SHALL never run Maven; eligible Maven entries SHALL retain their order, and an empty Maven selection SHALL receive explicit no-op feedback.

#### Scenario: Call a Maven-only endpoint directly
- **WHEN** a build, version, or POM mutation request directly targets a folder registration
- **THEN** the server SHALL reject the unsupported operation without modifying files or starting a Maven process.

### Requirement: Mixed-workspace hierarchy and order
Folder registrations SHALL not hide nested Maven build roots or participate as fake Maven dependency nodes. Automatic Maven ordering SHALL preserve folder positions while sorting eligible Maven entries; manual ordering SHALL remain available for all registrations.

#### Scenario: Folder contains Maven descendants
- **WHEN** an explicitly registered folder contains separately registered Maven projects
- **THEN** those Maven projects SHALL remain eligible build roots according to Maven hierarchy rather than being hidden by the folder registration.

#### Scenario: Automatically sort a mixed workspace
- **WHEN** the user calculates Maven dependency order in a mixed workspace
- **THEN** Maven entries SHALL be sorted without inventing dependencies for folders or moving folders from their existing positions.
