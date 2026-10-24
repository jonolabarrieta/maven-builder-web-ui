## ADDED Requirements

### Requirement: Complete Explicit Workspace Refresh Feedback

The system SHALL treat a user-initiated Refresh as one operation covering both project rescan and subsequent Git status update. While it is active, the interface SHALL show the current phase, prevent duplicate Refresh activation, and keep a loading indication visible until success or a reported failure. A normal form submission SHALL remain available when HTMX is unavailable.

#### Scenario: Refresh with many modules
- **WHEN** the user starts Refresh on a workspace with many modules
- **THEN** a loading indication SHALL remain visible throughout the rescan, even when it takes longer than two seconds
- **AND** the interface SHALL indicate that Git branches are updating after the project list has refreshed
- **AND** the operation SHALL indicate completion only after the Git phase has finished.

#### Scenario: Rescan fails
- **WHEN** the project rescan fails
- **THEN** the previous project list SHALL remain usable
- **AND** the loading indication SHALL end with a visible error and allow retry.

#### Scenario: Git phase fails after a successful rescan
- **WHEN** the rescan succeeds but the subsequent Git update fails
- **THEN** the refreshed project list SHALL remain visible
- **AND** the interface SHALL report that project refresh succeeded but branch status could not be updated
- **AND** the loading indication SHALL end and allow retry.

### Requirement: Correct Large-Workspace Results

The system SHALL preserve workspace project membership, top-level classification, child indicators, execution order, and enabled state when refresh and hierarchy calculations are optimized. Configured excluded subtrees SHALL be skipped during filesystem traversal.

#### Scenario: Nested projects and excluded directory
- **WHEN** Refresh scans a workspace containing nested Maven modules and an excluded directory
- **THEN** the refreshed list and hierarchy SHALL include the same non-excluded projects as before the optimization
- **AND** the scanner SHALL NOT descend into the excluded subtree.

#### Scenario: External manually added parent
- **WHEN** Refresh scans a workspace with a manually added parent outside its base directory
- **THEN** that parent and its eligible modules SHALL retain their existing membership and hierarchy behavior.
