# Spec Delta

## ADDED Requirements

### Requirement: Export and import RUN entries
Workspace export SHALL include the workspace's RUN entries in their configured order, with each entry's name, command, working directory, environment variables and Java option. A workspace with RUN entries SHALL be exported in the versioned structured format even when it has no groups.

An entry's linked project SHALL be referenced by file-local identity:
- the project's own key for a project the workspace owns
- the parent key plus the project key for a member of an imported group

Import SHALL recreate the entries and relink them to the newly registered projects. An entry whose project cannot be resolved, or whose working directory does not exist, SHALL still be imported, without a project link or with its directory as exported, and SHALL produce a warning. Versioned files without RUN entries SHALL keep importing unchanged. The paths-only export SHALL NOT include RUN entries.

#### Scenario: Round-trip RUN entries
- **WHEN** a workspace with the entries "api" (linked to an owned project) and "infra" (unlinked, `docker compose up`) is exported and imported
- **THEN** the new workspace SHALL contain both entries in the same order and with the same settings, and "api" SHALL be linked to the imported copy of its project.

#### Scenario: Ungrouped workspace with RUN entries
- **WHEN** a workspace without groups but with RUN entries is exported
- **THEN** the file SHALL use the versioned structured format so that the entries are preserved.

#### Scenario: Unresolvable project on import
- **WHEN** an imported entry references a project whose directory was skipped as unavailable
- **THEN** the entry SHALL be created without a project link, with its exported working directory, and the import SHALL report a warning naming the entry.

#### Scenario: Older versioned file
- **WHEN** a versioned file exported before this change is imported
- **THEN** the import SHALL succeed with no RUN entries.
