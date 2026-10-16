## Context

Builds execute through `ProcessExecutionService.executeCommandWithJavaHome`, which already records `startTime`, computes a duration, prints it to the log, and then discards it: `CommandResult` carries only `exitCode` and `output`. `BuildService` runs builds sequentially via `buildProjectsSequentially`, is entered from `buildWorkspaceSequentially` (Build All), `buildWorkspaceRange` (range modes), and `buildProject` (single project), and only broadcasts an `ActionSummary`/`BuildFailure` at the end. Nothing is persisted, so consecutive `git add`-style totals and per-project cost are invisible.

The app is Spring Boot 3.2.5 + Thymeleaf + HTMX + H2/JPA with a native-image profile. The recently added notification feature established the pattern for a new entity + repository + service + `@Controller` + hints, which this change follows.

## Goals / Non-Goals

**Goals**

- Persist one record per build execution of any type, with wall-clock total and per-project durations.
- Present a per-workspace history screen with total times and per-project averages.
- Keep recording cheap and non-blocking for the build path.

**Non-Goals**

- Predicting build times, alerts/thresholds, or charts/trend graphs.
- Retention limits or archival (the user chose to keep everything).
- Changing how builds execute, their ordering, skipping rules, or failure behavior.
- Timing Git actions; only Maven builds are recorded.
- Multi-user attribution.

## Decisions

### 1. Capture duration at the process boundary

Add `durationMs` to `CommandResult` and have `ProcessExecutionService` populate it from the duration it already measures (using `System.nanoTime` for the measurement). Keep a two-argument constructor delegating with `durationMs = 0` so the existing `new CommandResult(exitCode, output)` call sites and tests keep compiling.

**Alternative:** measure elapsed time in `BuildService` around each future. Rejected: it duplicates timekeeping, can drift from the real process window, and ignores the measurement the service already performs.

### 2. Model runs as `BuildRun` + `BuildRunProject`

`BuildRun` holds `workspaceId`, `workspaceName`, `type`, `startedAt`, `finishedAt`, `totalDurationMs`, `success`, and `builtProjectCount`. `BuildRunProject` is a real child entity (`@ManyToOne` `buildRun`) holding `artifactId`, `durationMs`, `success`, and `executionIndex`.

**Alternative:** an `@ElementCollection` of an embeddable timing. Rejected: JPQL cannot select an embeddable as a query root, which complicates the per-project aggregate; a child entity supports a clean projection query and future per-project filtering.

**Alternative:** store a JSON summary in one column. Rejected: opaque and hostile to aggregate queries.

### 3. Record type from the entry point

Add a `BuildRunType` enum: `FULL` (Build All), `RANGE` (range FROM/THROUGH), `PROJECT` (single-project build and range ONLY), and `SELECTION` (bulk "Build selected"). `buildWorkspaceSequentially` and `buildWorkspaceRange` pass the type down; `buildProject` records a `PROJECT` run, and the plain `buildProjectsSequentially(projects)` overload used by the bulk-action path records `SELECTION`. This labels every row without changing execution.

### 4. Record on completion, including failure

`BuildService` keeps a mutable accumulator (start instant, per-project timings, success). The run is persisted when the sequential chain finishes — on success or on the first failure (recording the projects that ran, including the failed one). Disabled projects are not timed or recorded. Evidence: `succeeded` list already tracks completed projects and the failure branch knows the failing project.

**Alternative:** persist the run row up front and update it. Rejected: `BuildService` builds asynchronously and a mid-build write adds a failure mode for little benefit; a run that never finishes (app killed) simply is not recorded.

### 5. Averages via an aggregate projection

`BuildRunRepository` exposes a JPQL projection returning `artifactId`, `avg(durationMs)`, `min`, `max`, and `count` grouped by artifact for a workspace. The history screen loads a page of runs (`@EntityGraph`/eager projects) plus the averages list.

**Alternative:** compute averages in Java by loading all runs. Rejected for unbounded growth; a database aggregate stays constant-cost as history grows.

### 6. Server-rendered history page with pagination

Add `GET /workspaces/{id}/build-history` rendering a Thymeleaf page: a runs table (newest first, 25 per page) where each row is a `<details>` expanding to its per-project timings, and a per-project averages table. Link it from the workspace "More actions" menu. Page-scoped pagination bounds rendering even though retention is unlimited.

**Alternative:** an HTMX fragment/modal for the details. Rejected as unnecessary: the data is a single eager fetch per page, and inline `<details>` keeps it keyboard-accessible with no extra endpoints.

## Risks / Trade-offs

- **Unbounded growth** (user chose no retention) → paginate the screen (25/page) and add an index on `(workspaceId, startedAt)`; document that pruning can be added later.
- **`CommandResult` constructor change** → keep the two-argument constructor and update `ProcessExecutionService` and its tests only.
- **New required `BuildService` dependency** → existing `@InjectMocks` tests must add the mock; no direct constructor call sites exist.
- **Crash mid-build loses the run** → acceptable; the run is only meaningful once finished.
- **Wall-clock vs sum of parts** → total is measured wall-clock (includes inter-project overhead), while project rows are individual process times; the UI labels totals as wall-clock so they are not assumed equal to the sum.
- **Native image reflection** → register `BuildRun`, `BuildRunProject`, and the stats projection in `NativeRuntimeHints`, mirroring existing entities.
- **Clock source** → durations use `System.nanoTime` (monotonic); timestamps use `Instant`.
