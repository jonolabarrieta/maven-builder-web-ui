# Tasks

## 1. Process-tree termination

- [x] 1.1 Create a `ProcessTrees.terminate(Process, Duration)` helper. It collects descendants first, requests a graceful termination, and forces termination after the timeout; on Windows it uses `taskkill /F /T`. Verify with a unit test that starts `sh -c "sleep 300 & sleep 300"` and asserts that no descendant is alive afterwards.
- [x] 1.2 Switch `ProcessExecutionService.killProcess` to the helper. Verify that the existing build tests pass and that a kill test leaves no child process alive.
- [x] 1.3 Extract the JAVA_HOME/PATH injection from `executeCoordinated` into a reusable helper. Verify that build behaviour is unchanged: the existing tests pass and the "Using JAVA_HOME override" log line is still emitted.

## 2. RUN entry persistence

- [x] 2.1 Add the `RunEntry` entity, with ordered env pairs, nullable project link, `position` and `lastSelected`, plus `RunEntryRepository`. Verify with a persistence test that saves and reloads an entry with env vars.
- [x] 2.2 Add `RunConfigurationService` with create, update, duplicate, delete and reorder operations, and validation:
  - the name is required and unique per workspace
  - the command is required
  - a project or a working directory is required
  - the working directory defaults to the project's directory
  - entries are rejected for parent workspaces

  Verify with service unit tests that cover each validation scenario in the spec.
- [x] 2.3 Keep entries when their linked project is removed: the link is a plain id resolved against the workspace composition, so a missing project shows as unlinked and the working directory is kept (workspace deletion deletes its entries explicitly). Verify with a test that deletes a linked project and asserts that the entry survives unlinked.

## 3. Run process registry

- [x] 3.1 Implement `RunProcessRegistry` with these parts:
  - per-workspace handles
  - shell invocation (`sh -c` / `cmd.exe /c`)
  - env and Java injection
  - a virtual-thread reader
  - running, stopped and crashed states, and exit codes
  - at most one live process per entry

  Verify with tests: starting `echo hi` reaches crashed with exit 0, and starting an entry that is already running is a no-op.
- [x] 3.2 Add a 2,000-line ring buffer with a `seq` counter for each handle, ANSI stripping, and publishing of lines and states to `/topic/run/{workspaceId}`. Verify with a test that 2,500 lines leave the last 2,000 lines with contiguous `seq` values.
- [x] 3.3 Implement stop and restart through `ProcessTrees`, with restart re-reading the saved entry. Verify that an edited command is used after restart, and that a stopped entry reaches the stopped state rather than crashed.
- [x] 3.4 Add a missing-working-directory error that starts no process. Verify with a test that asserts the error message names the entry and the directory.
- [x] 3.5 Terminate every handle on shutdown, through `@PreDestroy` plus an idempotent JVM shutdown hook. Verify with a test that calling close kills a running `sleep` tree.
- [x] 3.6 Verify that `/build/kill` and the `/topic/process` indicator ignore RUN processes. Add a test that starts a RUN entry, calls `killCurrentProcess()` and asserts that the entry is still running.

## 4. RUN controller endpoints

- [x] 4.1 Add a `RunController` with the entries fragment and the create, update and delete endpoints, all checking workspace ownership. Verify with MockMvc tests, including a rejected entry id that belongs to another workspace.
- [x] 4.2 Add the reorder and duplicate endpoints. Verify with MockMvc tests that check the resulting order and the copied fields.
- [x] 4.3 Add the `GET /run/modal` fragment, with preselection from `lastSelected` and running entries disabled, and `POST /run/start`, which stores `lastSelected`. Verify with MockMvc tests for the start, running and empty-config scenarios.
- [x] 4.4 Add the `POST /run/{entryId}/stop`, `POST /run/{entryId}/restart`, `GET /run/state` (snapshot with `lastSeq`) and `DELETE /run/{entryId}/log` endpoints. Verify with MockMvc tests, including that DELETE is rejected while the entry is running.

## 5. Build guard

- [x] 5.1 Publish `BuildFinishedEvent(workspaceId, runId, success)` from `BuildService` at its completion, failure and cancel points. Verify with a `BuildServiceRecordingTest`-style test that asserts the events.
- [x] 5.2 Add a guard service that, given the resolved targets, returns the running entries linked to them, and executes the `STOP_RESTART` path with a pending restart that a `BuildFinishedEvent` resolves. Verify with tests for restart on success and no restart on failure, including the summary message.
- [x] 5.3 Add the `runGuard` parameter and the `409` response to `POST /projects/{id}/build`. Verify with MockMvc tests for no guard, `IGNORE` and `STOP_RESTART`.
- [x] 5.4 Apply the same guard to `POST /workspaces/{id}/build-all` and `POST /workspaces/{wid}/projects/{pid}/build-range`. Verify with MockMvc tests for each endpoint.
- [x] 5.5 Apply the guard to `POST /workspaces/{id}/bulk-action` (build) and to `WorkspaceGroupController` `/{groupId}/action` (build). Verify with MockMvc tests.
- [x] 5.6 Add a guard dialog in `workspace-detail.html` and the JS, reusing `confirmation-modal.js`. It handles a 409 from any build trigger and re-submits with Stop, build and restart, Build anyway or Cancel. Verify manually in the running app, and with a `FrontendResourceContractTest` assertion that the handler exists.

## 6. Workspace page UI

- [x] 6.1 Add a RUN button next to Build All, and the launch modal (`workspace-detail.html` plus a new `static/js/run-processes.js`) loaded from `/run/modal`. Verify manually by starting a subset, and check that confirm is disabled when nothing is selected.
- [x] 6.2 Add a RUN configuration section or editor modal: an entry list with add, edit, duplicate, delete and drag reorder, a project picker that pre-fills the directory, rows for env pairs, a Java toggle, and the note that env vars are exported in plain text. Verify manually, and confirm that validation errors appear per field.
- [x] 6.3 Add RUN tabs to the console in `workspace-detail.html`: distinct styling, a state badge, Stop and Restart buttons, an exit code on crash, no closing while running (close button, middle click and close all), and no follow-toggle switching. Verify manually and update `FrontendResourceContractTest`.
- [x] 6.4 On load, subscribe to `/topic/run/{workspaceId}`, buffer messages, fetch `/run/state`, render the snapshot, and replay messages with `seq > lastSeq`. Closing a stopped tab calls DELETE. Verify manually that F5 with running entries restores their tabs with no duplicated lines, and that a closed stopped tab stays closed.

## 7. Export and import

- [x] 7.1 Add an optional `runs` field to `WorkspaceExchangeService.Definition`, and export entries with file-local project and parent keys. Make `WorkspaceService.exportWorkspace` choose v2 when run entries exist. Verify with `WorkspaceImportExportTest` cases for a grouped workspace and an ungrouped workspace with entries.
- [x] 7.2 On import, recreate entries, relink them through the new keys, and keep unresolved entries unlinked with a warning. Verify with round-trip, unresolved-project and pre-change v2 file tests.

## 8. Integration check

- [x] 8.1 Run `mvn test` and then an end-to-end manual pass with a real Spring Boot project and an npm folder:
  - start both
  - restart one
  - build the running project with Stop, build and restart
  - reload the page
  - exit the app, then confirm with `ps` that no child process remains and that the port is free
