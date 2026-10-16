## 1. Timing Capture

- [x] 1.1 Add a `durationMs` field to `CommandResult` with a three-argument constructor and a two-argument constructor that defaults `durationMs` to `0`.
- [x] 1.2 Populate `durationMs` in `ProcessExecutionService.executeCommandWithJavaHome` from the already-measured execution window (using `System.nanoTime`).

## 2. Persistence

- [x] 2.1 Add a `BuildRun` entity (`workspaceId`, `workspaceName`, `type`, `startedAt`, `finishedAt`, `totalDurationMs`, `success`, `builtProjectCount`) and a `BuildRunType` enum (`FULL`, `RANGE`, `PROJECT`).
- [x] 2.2 Add a `BuildRunProject` child entity (`buildRun`, `artifactId`, `durationMs`, `success`, `executionIndex`) with a `@ManyToOne` back-reference and cascade from `BuildRun`.
- [x] 2.3 Add a `BuildRunRepository` with a paginated newest-first query per workspace (fetching project timings) and an aggregate projection returning per-project average, min, max, and count.

## 3. Recording

- [x] 3.1 Add a `BuildHistoryService` with a run accumulator (start instant, per-project timings, success) and `record(...)`/`finish(...)` that persists the run, plus `runs(workspaceId, page)` and `projectAverages(workspaceId)` queries.
- [x] 3.2 Update `BuildService` to record a run for Build All (`FULL`), range builds (`RANGE`), and single-project builds (`PROJECT`), including the failure path, excluding disabled projects, and deriving workspace id/name from the projects.
- [x] 3.3 Add the `BuildHistoryService` mock to `BuildServiceGitSummaryTest` (and any other `@InjectMocks` construction of `BuildService`).

## 4. Screen and Navigation

- [x] 4.1 Add a `BuildHistoryController` exposing `GET /workspaces/{id}/build-history` (with a page parameter) that loads the workspace, the run page, and the per-project averages.
- [x] 4.2 Add `build-history.html`: a runs table (newest first) with expandable per-project timings, a per-project averages table, an empty state, and pagination controls.
- [x] 4.3 Add a "Build history" link to the workspace "More actions" menu in `workspace-detail.html`.
- [x] 4.4 Register `BuildRun`, `BuildRunProject`, and the averages projection in `NativeRuntimeHints` for the native image profile.

## 5. Tests and Verification

- [x] 5.1 Add service/repository tests for recording runs of each type, excluding disabled projects, capturing partial timings on failure, and computing per-project averages/min/max/count.
- [x] 5.2 Add a controller/render test that exercises `GET /workspaces/{id}/build-history`, including the empty state and a populated history with expandable timings.
- [x] 5.3 Run the full test suite, verify H2 startup with the new tables, and run `openspec validate add-build-time-history`.
