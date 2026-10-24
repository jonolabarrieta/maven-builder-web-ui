# workspace-project-selection Specification

## Purpose

Preserve consistent workspace project selection across background Git updates, explicit refreshes, and bulk actions.

## Requirements

### Requirement: Stable Workspace Project Selection

The system SHALL keep the selected top-level project IDs in page-scoped state independent of replaceable project-list markup. The visible checkboxes, selected count, Select All control, and bulk-action inputs SHALL represent the same set.

#### Scenario: Select projects while Git status updates
- **WHEN** the user selects or deselects projects while the background Git update is pending or completing
- **THEN** the completed selection SHALL remain visible and available to bulk actions
- **AND** the branch update SHALL NOT replace or reset the project checkboxes.

#### Scenario: Select All changes selection
- **WHEN** the user changes Select All
- **THEN** every currently displayed top-level project SHALL gain or lose selection accordingly
- **AND** the selected count and indeterminate state SHALL reflect the result.

#### Scenario: Explicit refresh changes the project list
- **WHEN** an explicit Refresh returns a new project list
- **THEN** selected IDs still present in that list SHALL remain selected
- **AND** IDs no longer present SHALL be removed from selection
- **AND** the count, Select All state, and bulk-action inputs SHALL be reconciled before the refreshed list becomes interactive.

#### Scenario: Bulk action starts
- **WHEN** the user starts a bulk action
- **THEN** its project IDs SHALL be exactly the currently selected, displayed project IDs.
