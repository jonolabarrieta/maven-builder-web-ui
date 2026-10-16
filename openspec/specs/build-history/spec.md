# build-history Specification

## Purpose
TBD - created by archiving change add-build-time-history. Update Purpose after archive.
## Requirements
### Requirement: Build Runs Are Recorded
The system SHALL persist a build run each time a Maven build executes, capturing the workspace, the run type (Build All, range, single project, or an arbitrary selection), the start and finish timestamps, the wall-clock total duration, the success state, and one timing entry per project that was actually built. Projects that are skipped because they are disabled SHALL NOT be recorded.

#### Scenario: Build All records a full run
- **WHEN** the user starts a Build All for a workspace
- **THEN** the system persists a run of type `FULL` with its wall-clock total, and one timing entry for each enabled project containing its artifact ID, execution index, duration, and success.

#### Scenario: Range build records a range run
- **WHEN** the user builds a range (from/through a module)
- **THEN** the system persists a run of type `RANGE` covering only the projects in that range.

#### Scenario: Single-project build records a project run
- **WHEN** the user builds a single project
- **THEN** the system persists a run of type `PROJECT` containing only that project's timing.

#### Scenario: Selected projects record a selection run
- **WHEN** the user builds an arbitrary selection of projects (bulk "Build selected")
- **THEN** the system persists a run of type `SELECTION` covering only the selected projects.

#### Scenario: Disabled projects are excluded
- **WHEN** a build run encounters a project that is disabled
- **THEN** the run does not include a timing entry for that project.

#### Scenario: Failed build records partial timings
- **WHEN** a build stops because a project failed
- **THEN** the system persists the run with `success=false`, a timing entry for every project that ran including the failed one, and no entries for projects that were not built.

#### Scenario: Per-project durations come from the process execution
- **WHEN** a project's Maven command completes
- **THEN** its recorded duration is the measured execution time of that command.

### Requirement: Build History Screen
The system SHALL provide a per-workspace build history screen reachable from the workspace view. The screen SHALL list recorded runs newest first, showing for each the date/time, run type, number of projects built, total wall-clock duration, and success state, and SHALL let the user expand a run to see its per-project timings.

#### Scenario: Screen is reachable from the workspace
- **WHEN** the user is on the workspace detail page
- **THEN** a "Build history" entry is available that navigates to the workspace build history screen.

#### Scenario: Runs listed newest first
- **WHEN** the user opens the build history screen
- **THEN** recorded runs are listed newest first with their type, project count, total duration, and success state.

#### Scenario: Expanding a run shows project timings
- **WHEN** the user expands a run
- **THEN** the screen shows each built project's artifact ID and duration for that run, in execution order.

#### Scenario: Empty history
- **WHEN** the workspace has no recorded runs
- **THEN** the screen shows an empty state instead of an empty table.

#### Scenario: History is paginated
- **WHEN** more runs exist than fit on one page
- **THEN** the screen pages the run list and lets the user move between pages.

### Requirement: Per-Project Build Averages
The system SHALL show, on the workspace build history screen, an average build time per project computed across all recorded runs of that workspace, including the number of runs measured and the minimum and maximum observed durations.

#### Scenario: Averages computed across runs
- **WHEN** the user opens the build history screen for a workspace
- **THEN** the screen shows, for each project that has been built at least once in that workspace, its average duration, run count, minimum duration, and maximum duration.

#### Scenario: New runs update the averages
- **WHEN** a new build run is recorded for the workspace
- **THEN** the per-project averages reflect the new run on the next load of the screen.

