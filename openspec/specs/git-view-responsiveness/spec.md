# git-view-responsiveness Specification

## Purpose
Keep the workspace Git view fast and visibly responsive with many repositories, so local reads never appear frozen while remote operations or large comparisons run.

## Requirements

### Requirement: Visible comparison loading state
When the user selects a file, the view SHALL immediately show the newly selected path and area and mark the previous comparison as outdated. A loading indicator SHALL appear if the comparison has not arrived within a short delay. A comparison that fails or exceeds its time limit SHALL show an error state with a retry control instead of leaving the previous comparison displayed as if it were current.

#### Scenario: Slow comparison
- **WHEN** the user selects a file whose comparison takes several seconds to load
- **THEN** the file name SHALL update at once, the previous comparison SHALL be visibly dimmed and non-interactive, and a loading indicator SHALL be shown until the new comparison renders

#### Scenario: Comparison fails
- **WHEN** the comparison request fails or times out
- **THEN** the view SHALL show the reason and a retry control, and SHALL not keep the earlier file's lines selectable

### Requirement: Superseded comparisons are cancelled
Selecting another file or repository SHALL cancel any in-flight comparison request for the previous selection. Selecting a file in a different repository SHALL load only that file's comparison, not an intermediate default file.

#### Scenario: Rapid file switching
- **WHEN** the user selects three files in quick succession
- **THEN** only the last file's comparison SHALL be rendered and the earlier requests SHALL be abandoned without displaying their results

#### Scenario: File in another repository
- **WHEN** the user selects a file under a repository other than the active one
- **THEN** the view SHALL request and render only that file's comparison

### Requirement: Remote operations do not block local reads
Long-running remote operations (fetch, pull, push, Commit & Push, publish) SHALL use capacity separate from local status, comparison and staging reads. Running the maximum number of remote operations SHALL not delay local reads of other repositories.

#### Scenario: Bulk fetch in progress
- **WHEN** a slow fetch is running for every selected repository
- **THEN** status refreshes and file comparisons for any repository SHALL still complete within their normal local time

### Requirement: Lightweight status for listing
Status used to list repositories and files SHALL not require reading the full content of every changed file. Mutations SHALL still validate the reviewed snapshot with content-accurate identities before changing the index, working tree or history.

#### Scenario: Repository with large untracked files
- **WHEN** a repository contains many large changed or untracked files
- **THEN** listing its status SHALL not read those files' contents, while a later stage, discard or commit SHALL still reject a snapshot that changed after review

### Requirement: Incremental rendering for large workspaces
Receiving the status of one repository SHALL update only that repository's entry in the list. Search input SHALL be debounced. The page SHALL remain interactive while statuses for a large workspace arrive.

#### Scenario: Hundred repositories load
- **WHEN** a workspace with one hundred repositories loads its statuses
- **THEN** each arriving status SHALL update its own entry without rebuilding other entries, and the user SHALL be able to select a repository before all statuses arrive

### Requirement: Pushed operation progress
Operation progress SHALL be delivered to the open Git page as it changes without fixed short-interval polling. If push delivery is unavailable, the page SHALL fall back to polling. An operation that is no longer known to the server SHALL end with an explicit expired or uncertain state rather than a generic error.

#### Scenario: Push channel unavailable
- **WHEN** the page cannot establish the push channel
- **THEN** operation progress SHALL still be reported through polling

#### Scenario: Operation expired during server restart
- **WHEN** the server no longer knows a running operation identity
- **THEN** the view SHALL report the outcome as uncertain, refresh the affected repositories, and clear their busy state
