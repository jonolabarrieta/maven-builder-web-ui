## Why

Builds stream logs and a final summary, but no timing is kept. Users cannot tell whether a workspace build got slower, which project dominates the time, or how long a full build usually takes. `ProcessExecutionService` already measures each command's duration and discards it, so the data is available but never persisted. A build-time history makes build performance visible and comparable over time.

## What Changes

- Capture per-command duration by adding it to `CommandResult`, and record a build run every time a build executes: **Build All**, **range** builds, and **single-project** builds, each labelled with its type.
- Persist each run with its wall-clock total, start time, success state, and one timing row per built project (artifact ID, duration, success, execution index). Disabled/skipped projects are not recorded.
- Add a new **Build history** screen per workspace showing runs newest first (date, type, project count, total time, status), each expandable to its per-project timings.
- Add a **per-project average** table (average, min, max, run count) computed across all recorded runs of the workspace.
- Link the new screen from the workspace navigation.
- Keep the entire history (no retention limit).

## Capabilities

### New Capabilities

- `build-history`: Record build runs with per-project timings and present a workspace screen with run history and per-project averages.

### Modified Capabilities

<!-- None: existing build behavior and its specs are unchanged; timing capture is additive. -->

## Impact

- **Model**: `CommandResult` gains `durationMs`; `ProcessExecutionService` populates it.
- **Backend**: new `BuildRun` + `BuildRunProject` entities, `BuildRunRepository`, `BuildHistoryService`, and a `BuildHistoryController` (`GET /workspaces/{id}/build-history`). `BuildService` records a run for full, range, and single-project builds.
- **Frontend**: new `build-history.html` page and a "Build history" link in the workspace actions menu.
- **Native image**: register the new entities in `NativeRuntimeHints`.
- **Tests**: service/repository tests for recording and averages, a controller/render test for the screen, and updates to existing `BuildService` tests for the new dependency.
