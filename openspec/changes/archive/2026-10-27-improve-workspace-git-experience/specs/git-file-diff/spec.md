## ADDED Requirements

### Requirement: Expandable comparison context
Text comparisons SHALL allow revealing unchanged lines hidden between and around change blocks, either incrementally or as the full file, without changing change-block identities, line selection or staging behavior. Revealed lines SHALL come from the same snapshot as the comparison.

#### Scenario: Reveal lines between two blocks
- **WHEN** the user expands the gap between two change blocks
- **THEN** the hidden unchanged lines SHALL appear with correct old and new line numbers and existing line selections SHALL remain

#### Scenario: Show full file
- **WHEN** the user chooses to show the full file
- **THEN** every line of the compared versions SHALL be displayed with changes in place

#### Scenario: Snapshot changed before expansion
- **WHEN** the file changed after the comparison was loaded and the user expands context
- **THEN** the view SHALL report that the comparison must be refreshed rather than mix lines from different versions
