## ADDED Requirements

### Requirement: Local project groups
The system SHALL allow creating, renaming, ordering, and removing named local groups within an ordinary workspace, moving its own project roots into or out of them, and ordering their members. A root SHALL belong to at most one local group; its Maven descendants SHALL follow it. Groups SHALL NOT contain groups. Deleting a local group SHALL leave its projects ungrouped at the group's former position without deleting registrations or files.

#### Scenario: Group projects locally
- **WHEN** A moves roots X and Y into local group L and reorders them
- **THEN** A SHALL display L with that order and another workspace's organization SHALL remain unchanged.

#### Scenario: Move a Maven root
- **WHEN** a root with nested modules is moved into a group
- **THEN** its module hierarchy SHALL remain intact inside that group and independent placement of its descendants SHALL be rejected.

#### Scenario: Ungroup without deleting
- **WHEN** local group L containing X and Y is removed
- **THEN** X and Y SHALL remain registered, in order, as adjacent ungrouped entries at L's former position.

### Requirement: Deterministic group composition
The system SHALL order groups and ungrouped project roots as blocks and flatten them into a deterministic effective project sequence. Imported members SHALL follow their parent's current order. Persisted local membership, ordering, enabled state, explicit non-Maven registrations, and Maven hierarchy SHALL survive refresh and restart. Dependency recalculation SHALL reject incompatible cross-block ordering with feedback and preserve the previous order rather than silently changing group membership.

#### Scenario: Flatten blocks
- **WHEN** the workspace order is root A, local group containing B and C, imported group containing D and E, then root F
- **THEN** its effective order SHALL be A, B, C, D, E, F with each root's existing module traversal retained.

#### Scenario: Refresh an imported group
- **WHEN** a consumer refreshes after a parent adds a project
- **THEN** the imported group SHALL show the new member and SHALL NOT scan the consumer's base folder to change the parent's membership or routes.

#### Scenario: Dependency order conflicts with blocks
- **WHEN** dependency recalculation requires interleaving members of two groups
- **THEN** the system SHALL explain the conflict and preserve the previous sequence and memberships.

### Requirement: Independent consumer configuration
The system SHALL keep imported group position, label, and project enabled overrides local to the consumer. Imported projects without an explicit local enabled override SHALL inherit the parent's enabled state. Group enable or disable SHALL apply to its current members; subsequently added members SHALL inherit their source state. Editing imported source structure SHALL require navigating to the parent.

#### Scenario: Disable one imported project
- **WHEN** A disables imported project X from parent P
- **THEN** A SHALL skip X in actions that respect disabled state while P and consumer B SHALL retain their own effective enabled states.

#### Scenario: Add a member after disabling a group
- **WHEN** A disables a group's current members and the parent later adds enabled project Y
- **THEN** Y SHALL inherit its enabled parent state and existing local member overrides SHALL remain unchanged.

### Requirement: Collapsible group presentation
Local and imported groups SHALL have a header, origin where applicable, member count, actions, and an accessible expand/collapse control. Expanded members SHALL retain their applicable individual actions and Maven module navigation. Collapse SHALL hide members without modifying selection, enabled state, or action scope; it SHALL survive HTMX replacement, Git updates, and reopening the workspace in the same browser.

#### Scenario: Operate on a collapsed group
- **WHEN** the user collapses a group and activates its build action
- **THEN** all eligible group members SHALL be considered even though their rows are hidden.

#### Scenario: Use keyboard to expand
- **WHEN** a keyboard user activates the group disclosure control
- **THEN** member visibility and aria-expanded SHALL update consistently and the control SHALL identify the region it controls.

#### Scenario: Refresh preserves disclosure
- **WHEN** a collapsed group survives a project-list refresh or workspace reopening
- **THEN** it SHALL remain collapsed in that browser without losing retained project selection during the page refresh.
