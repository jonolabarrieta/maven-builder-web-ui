## MODIFIED Requirements

### Requirement: Stable Workspace Project Selection
The system SHALL keep selected top-level project entries in page-scoped state independent of replaceable markup. Each entry SHALL identify its consumer, optional group, source and project. Visible checkboxes, selected count, Select All, group mixed states and bulk-action inputs SHALL represent the same set, including entries inside collapsed groups. Group selection SHALL select its current project roots; collapse SHALL NOT change selection. Select All SHALL cover all current top-level entries in the workspace composition, independent of collapse.

#### Scenario: Select projects while Git status updates
- **WHEN** the user selects or deselects projects while the background Git update is pending or completing
- **THEN** the completed selection SHALL remain visible and available to bulk actions
- **AND** the branch update SHALL NOT replace or reset the project checkboxes.

#### Scenario: Select All changes selection
- **WHEN** the user changes Select All
- **THEN** every current top-level entry, including members of collapsed groups, SHALL gain or lose selection accordingly
- **AND** the selected count and workspace and group indeterminate states SHALL reflect the result.

#### Scenario: Explicit refresh changes the project list
- **WHEN** an explicit Refresh returns a new project composition
- **THEN** selected entries still present SHALL remain selected
- **AND** removed entries SHALL be removed from selection and newly added entries SHALL remain unselected
- **AND** the count, Select All, group states and bulk-action inputs SHALL be reconciled before the refreshed list becomes interactive.

#### Scenario: Bulk action starts
- **WHEN** the user starts a bulk action
- **THEN** its scope SHALL be exactly the currently selected entries in the composition, including collapsed members, subject to server membership validation and operation eligibility.

#### Scenario: Select a group and deselect one member
- **WHEN** a user selects group G and then deselects one of its members
- **THEN** G SHALL show a mixed selection state and the selected count and action payload SHALL exclude that member.

#### Scenario: Remove an imported group
- **WHEN** a selected imported group is removed
- **THEN** only its entry identities SHALL be removed from the current page selection and unrelated selections SHALL remain.
