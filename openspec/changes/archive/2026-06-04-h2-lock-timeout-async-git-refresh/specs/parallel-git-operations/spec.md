## MODIFIED Requirements

### Requirement: Parallel Git Operations
The system SHALL execute Git operations (fetch, pull, checkout, discard, and restore/unstage) in parallel across selected projects when a bulk Git action is triggered, and all database updates resulting from Git operations SHALL run in isolated, short transactions to prevent table lock-ups.

#### Scenario: Parallel Git Fetch Execution
- **WHEN** the user selects projects A, B, and C and triggers a bulk "Git Fetch" action
- **THEN** the system SHALL start the Git fetch command on A, B, and C concurrently without waiting for each other to finish.

#### Scenario: Parallel Git Pull Execution
- **WHEN** the user selects projects A, B, and C and triggers a bulk "Git Pull" action
- **THEN** the system SHALL start the Git pull command on A, B, and C concurrently and aggregate the changes.

#### Scenario: Parallel Git Checkout Execution
- **WHEN** the user selects projects A, B, and C and performs a bulk checkout to a target branch
- **THEN** the system SHALL execute the Git checkout command on A, B, and C concurrently.

#### Scenario: Sequential Build Execution
- **WHEN** the user selects projects A, B, and C and triggers a bulk "Build" action
- **THEN** the system SHALL build A, B, and C sequentially in the defined execution/topological order, ensuring dependency order is strictly respected.
