## ADDED Requirements

### Requirement: Parent workspace administration
The system SHALL provide a global Parent workspaces tab to list, create, rename, edit, and delete reusable parent workspaces and show their consuming workspaces. Parent project management SHALL retain existing Maven discovery, explicit non-Maven registration, exclusions, ordering, and project metadata behavior. Ordinary workspaces SHALL remain separately listed.

#### Scenario: Create and maintain a parent
- **WHEN** the user creates a parent in the Parent workspaces tab and adds projects
- **THEN** the parent and its ordered projects SHALL persist across restart and be available for import into ordinary workspaces.

#### Scenario: Inspect parent consumers
- **WHEN** the user opens a parent used by workspaces A and B
- **THEN** its administration SHALL identify A and B and provide navigation to them.

### Requirement: Shared parent references
The system SHALL allow an ordinary workspace to import a parent as a named imported group without copying or transferring ownership of its projects. The same parent SHALL be importable by multiple workspaces, once per consumer. Parent changes SHALL be reflected when a consumer is opened or explicitly refreshed. Consumers SHALL edit imported membership, paths, and source order through the parent editor.

#### Scenario: Update a reused collection
- **WHEN** parent P is imported in A and B and its project list or order changes
- **THEN** opening or refreshing A and B SHALL resolve P's current ordered collection without reimporting it.

#### Scenario: Prevent repeated or nested imports
- **WHEN** a request imports a parent twice into one consumer, imports a workspace into itself, or imports any workspace into a parent
- **THEN** the system SHALL reject it without changing the existing structure.

### Requirement: Reference-safe lifecycle
The system SHALL block deletion of a referenced parent and identify its consumers. Removing an imported group or deleting its consumer SHALL preserve the parent and its projects. Duplicating a consumer SHALL copy its local structure and configuration while referencing the same parents; duplicating a parent SHALL copy its project definitions without copying its consumers.

#### Scenario: Delete a referenced parent
- **WHEN** the user tries to delete a parent referenced by A
- **THEN** deletion SHALL be rejected and A SHALL be identified as a consumer.

#### Scenario: Remove an import
- **WHEN** A removes its imported group for P
- **THEN** only A's reference and local import configuration SHALL be removed and P and its other consumers SHALL remain usable.

#### Scenario: Duplicate a consumer
- **WHEN** A with local group L and imported parent P is duplicated
- **THEN** the copy SHALL have independent local projects, groups and overrides and SHALL reference the existing P.
