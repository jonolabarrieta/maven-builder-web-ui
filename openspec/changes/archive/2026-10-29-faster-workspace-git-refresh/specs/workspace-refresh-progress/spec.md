# Spec Delta

## ADDED Requirements

### Requirement: Rescan does not query Git
The project rescan phase of a workspace refresh SHALL NOT run Git. It SHALL update membership, Maven metadata, hierarchy and order from the filesystem only, and SHALL keep each existing project's stored branch unchanged. The branch shown for every displayed project SHALL be determined by the Git phase that follows the rescan. A project discovered by the rescan SHALL show its branch once that phase completes. Importing a workspace and adding projects by path are not part of the rescan and SHALL keep resolving branches from disk.

#### Scenario: Refresh runs Git only in the Git phase
- **WHEN** the user refreshes a workspace of 75 projects, each in its own repository
- **THEN** no Git process SHALL start before the refreshed project list is shown, and the Git phase SHALL then update each project's branch.

#### Scenario: Newly discovered project
- **WHEN** the rescan discovers a project that was not registered before
- **THEN** it SHALL appear in the refreshed list, and its branch SHALL be filled in when the Git phase completes.

#### Scenario: Git phase fails
- **WHEN** the rescan succeeds and the Git phase fails
- **THEN** existing projects SHALL keep their previously stored branches, and the refresh SHALL report that branch status could not be updated.
