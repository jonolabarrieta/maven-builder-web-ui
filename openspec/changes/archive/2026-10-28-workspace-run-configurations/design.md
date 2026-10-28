# Design

## Context

See proposal.md for the motivation. The current process layer has these constraints:

- **One global process set.** `ProcessExecutionService` keeps every process it starts in a single `activeProcesses` set and runs each command on the shared `@Async` pool, which blocks a thread until the process exits.
  - `/build/kill` calls `killCurrentProcess()`, which kills everything in the set.
  - `/topic/process` publishes "one active PID", which the UI interprets as "a build is running".
- **Parent-only kill on Linux and macOS.** `killProcess` runs `process.destroy()` on the parent only. Windows already kills the whole tree with `taskkill /T`.
- **Global log topic.** Log lines are published to the global `/topic/logs` topic as `LogMessage(label, content)`. The console in `workspace-detail.html` keeps tabs and lines in browser memory only, so they are lost on reload.
- **Export formats.** Workspace export uses the legacy text format unless the workspace has groups or is a parent. Otherwise it uses the JSON v2 `Bundle` in `WorkspaceExchangeService`, with file-local keys (`project0`, `parent0`).
- **Build triggers.** Builds are started from several endpoints: `/projects/{id}/build`, `/workspaces/{id}/build-all`, `build-range`, `bulk-action`, and group build in `WorkspaceGroupController`. Each runs asynchronously and returns immediately.

## Goals / Non-Goals

**Goals:**
- RUN processes live in a subsystem that build code cannot see or kill by accident, and that does not hold pool threads.
- The server is the source of truth for RUN state and recent output, so that a page reload loses nothing.
- Process-tree termination is reliable on every operating system.

**Non-Goals:**
- Startup dependencies, readiness or health checks, automatic restart on crash, and port detection. This was option C in the discussion and is deferred.
- Persisting RUN processes across application restarts, or re-attaching to orphaned processes.
- Interactive stdin for RUN processes.
- The build guard on build continuations (retry or skip). A continuation resumes a build the user already started.

## Decisions

### 1. Separate `RunProcessRegistry` instead of extending `ProcessExecutionService`
A new singleton service owns `Map<workspaceId, Map<entryId, RunHandle>>`. Each `RunHandle` holds the `Process`, its state, its exit code, its start time and a log ring buffer. Each process is read by its own virtual thread (`Thread.ofVirtual()`), which is already used in `ProcessExecutionService`.

The registry does not touch `activeProcesses`. As a result, `/build/kill` and `/topic/process` stay build-only without any change.

- *Alternative:* add a "kind" flag to `activeProcesses`. Rejected because every existing caller (kill, PID indicator, `finally` cleanup) would need to filter on the flag, and missing one reintroduces the bug.
- *Alternative:* reuse the `@Async` pool. Rejected because a long-running process would block a pool thread indefinitely.

### 2. Process-tree kill helper shared with builds
A `ProcessTrees.terminate(Process, Duration)` helper works as follows:

1. Collect `process.descendants()` before signalling. The children re-parent once the parent dies, so they must be collected first.
2. Call `destroy()` on the descendants and the parent, and wait up to 5 seconds.
3. Call `destroyForcibly()` on any process still alive.

On Windows the helper keeps using `taskkill /F /T /PID`. `ProcessExecutionService.killProcess` also switches to this helper, which fixes the existing orphan-JVM issue for builds. That behaviour change is invisible apart from the fix.

- *Alternative:* start the process in its own process group (`setsid`) and kill the group. Rejected because the JDK has no portable API for it and it would need a wrapper script per operating system.

### 3. Shell invocation
The command runs as `sh -c <command>` on Linux and macOS, and as `cmd.exe /c <command>` on Windows. Environment variables are merged into `ProcessBuilder.environment()`. JAVA_HOME and PATH injection reuses the logic already in `executeCoordinated`, extracted into a shared helper.

- *Alternative:* split the command into arguments. Rejected because users expect `&&`, pipes and `$VAR` to work as they do in a terminal.

### 4. Data model
- **`RunEntry` entity:** `id`, `workspace` (ManyToOne, owner), `name`, `position`, `project` (nullable ManyToOne to `MavenProject`), `workingDirectory`, `command` (length 4000), `useWorkspaceJava`, `lastSelected` (boolean), and `env` as an `@ElementCollection` of ordered `(name, value)` pairs.
- **Linked imported projects:** the entry links to the parent's `MavenProject` row.
- **Project removal:** the FK is nulled through a pre-removal hook in the project-delete path. `workingDirectory` is always stored, so the entry still works after the link is removed.
- **`lastSelected`:** stored on the server instead of in `localStorage`, so the preselection follows the workspace across browsers and survives export.
- **Schema creation:** tables are created by the existing JPA DDL auto-update. No existing data needs migration.

### 5. Logs: per-workspace topic plus snapshot endpoint
Each line is published to `/topic/run/{workspaceId}` as `{entryId, seq, content}`. State changes are published to the same topic as `{entryId, state, exitCode, pid}`. Each handle has a monotonically increasing `seq`.

On page load, the client:
1. subscribes to the topic and buffers incoming messages
2. calls `GET /workspaces/{id}/run/state`, which returns each entry's state and buffer with the last `seq`
3. renders the snapshot, then replays the buffered live messages with `seq > lastSeq`

This prevents both gaps and duplicates. The ring buffer holds 2,000 lines per handle, using an `ArrayDeque` under the handle lock. Closing a stopped tab calls `DELETE /workspaces/{id}/run/{entryId}/log`, which drops the handle, so the tab does not come back on reload.

- *Alternative:* reuse `/topic/logs` with a workspace field. Rejected because every build listener would receive RUN traffic and would have to filter it.

### 6. Endpoints (`RunController`)
| Method | Path | Purpose |
|---|---|---|
| GET | `/workspaces/{id}/run/entries` | Fragment for the RUN configuration section |
| POST / PUT / DELETE | `/workspaces/{id}/run/entries[/{entryId}]` | Create, update and delete entries |
| POST | `/workspaces/{id}/run/entries/order` | Reorder entries |
| POST | `/workspaces/{id}/run/entries/{entryId}/duplicate` | Duplicate an entry |
| GET | `/workspaces/{id}/run/modal` | Launch modal fragment, with preselection and running state |
| POST | `/workspaces/{id}/run/start` | Start the selected entry ids (`entryIds[]`) and store `lastSelected` |
| POST | `/workspaces/{id}/run/{entryId}/stop` | Stop one entry |
| POST | `/workspaces/{id}/run/{entryId}/restart` | Restart one entry |
| GET | `/workspaces/{id}/run/state` | Snapshot JSON for reload |
| DELETE | `/workspaces/{id}/run/{entryId}/log` | Dismiss a stopped or crashed log |

Every endpoint checks that the entry belongs to the workspace in the path. The UI follows the existing HTMX and modal patterns (`app-dialogs.js`, `confirmation-modal.js`). The RUN configuration editor is a new section in the workspace page, opened from a new RUN menu next to Build All.

### 7. Build guard as a server-checked precondition
The build endpoints accept an optional `runGuard` parameter with the value `STOP_RESTART` or `IGNORE`.

- **No `runGuard`, running entries involved:** the server resolves the build targets and, if running entries are linked to them, returns `409` with JSON listing those entries. The client opens the guard dialog and re-submits the request with the chosen value. The client starts nothing on Cancel.
- **`STOP_RESTART`:** the server stops the entries, registers a pending restart keyed to the build run, and starts the build.
- **Build completion:** `BuildService` publishes a Spring `BuildFinishedEvent(workspaceId, runId, success)` at its existing completion and failure points. The registry listens for it. On success it restarts the entries. On failure or cancellation it sends an action summary naming the entries that were not restarted.

Checking on the server keeps the decision correct even if the client's view of running entries is stale.

- *Alternative:* a client-only check. Rejected because the client does not know the resolved targets for workspace, group and range builds.

### 8. Shutdown
The registry's `@PreDestroy` method terminates every handle using the tree helper. A JVM shutdown hook does the same, as a fallback for paths that skip the Spring lifecycle, such as the tray exit and `System.exit`. The terminate operation is idempotent.

### 9. Export format
`Definition` gains an optional field `List<Run> runs`, where `Run(name, command, workingDirectory, env, useWorkspaceJava, project, parent)`:
- For an owned project, `project` is its file-local key and `parent` is null.
- For an imported member, `parent` is the parent key and `project` is that parent's project key.

`exportWorkspace` chooses v2 when the workspace has groups, is a parent, or has run entries. The bundle `version` stays at 2, because the field is optional and older v2 files deserialise with `runs == null`.

## Risks / Trade-offs

- **[Risk]** Environment values, such as tokens, end up in exported files. → **Mitigation:** show a note in the editor and on export that environment variables are exported in plain text. Encryption is out of scope.
- **[Risk]** `sh -c` changes the signal target. Some shells exec the last command and some do not, so the PID may be the shell's. → **Mitigation:** tree termination covers both cases.
- **[Risk]** A process that spawns detached daemons (for example `docker compose up -d`) leaves children outside the tree. → **Mitigation:** document this as expected. The entry ends as crashed with exit 0, because the user did not stop it.
- **[Risk]** A force-kill at shutdown may skip graceful cleanup in the services. → **Mitigation:** request a graceful termination first. The 5-second timeout is a constant that can be tuned later.
- **[Trade-off]** Log buffers live in memory: at most 2,000 lines × number of entries. This is acceptable for a desktop tool.
- **[Trade-off]** A clean exit with code 0 is shown as crashed. This keeps the state model at three states and makes an unexpected exit visible.

## Migration Plan

New tables are created automatically on first start. No existing data changes. To roll back, deploy the previous version: the extra tables are ignored, and v2 files that contain `runs` remain importable by older versions, since Spring Boot's default `ObjectMapper` ignores unknown properties.
