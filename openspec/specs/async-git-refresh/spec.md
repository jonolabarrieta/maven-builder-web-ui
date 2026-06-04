# async-git-refresh Specification

## Purpose
TBD - created by archiving change h2-lock-timeout-async-git-refresh. Update Purpose after archive.
## Requirements
### Requirement: Asynchronous Git Status Refresh
The system SHALL load the workspace detail page instantly without blocking on Git operations, using the cached branch names from the database, and then trigger an asynchronous background request to query the actual Git branches and update the UI dynamically.

#### Scenario: Background Refresh on Page Load
- **WHEN** the user loads the workspace details page
- **THEN** the page SHALL render immediately with cached database information and immediately launch a background GET request to `/workspaces/{id}/git-status-table`.

#### Scenario: Dynamic Swap on Background Load
- **WHEN** the background GET request to `/workspaces/{id}/git-status-table` returns successfully
- **THEN** the table body containing the project list SHALL be replaced with the updated HTML fragment containing the newly resolved branch names, without triggering a full-screen loading spinner.

