## Context

The workspace page receives streamed command output over `/topic/logs`, renders it in named tabs, and clears that in-browser state before a new build. Builds already execute sequentially in stored execution order via `BuildService.buildProjectsSequentially`. Project actions are served by `WorkspaceController`; Explorer and VS Code already use OS-specific `ProcessBuilder` commands. Workspace export currently includes metadata and paths, while Export Order emits artifact IDs.

## Goals / Non-Goals

**Goals:**

- Preserve the visible build output in a portable text download without adding server-side log persistence.
- Launch a usable terminal rooted at a selected project directory on Windows, macOS, and common Linux desktops.
- Let each project initiate an inclusive, sequential build range based on the displayed execution order.
- Produce a path-only, ordered workspace export.

**Non-Goals:**

- Persisting build logs across refreshes/restarts or supplying historical log downloads.
- Executing arbitrary terminal commands or embedding a terminal in the browser.
- Changing Maven commands, profiles, retry semantics, project ordering, or building a dependency graph subset.
- Making the path-only export importable as a full workspace configuration.

## Decisions

### 1. Download logs in the browser

The log component will retain the appended lines per tab and build a UTF-8 `Blob` when the download button is clicked. The output will include a small header (workspace, download time) and labeled tab sections in the same order shown, so the current build output is complete even when the user has switched tabs. A client-side blob avoids storing potentially large, transient logs on the server and exactly reflects the UI session. The button is disabled or reports that no captured output exists when appropriate.

### 2. Encapsulate local launch commands

Terminal launching will move into a narrowly scoped service/helper rather than adding another `ProcessBuilder` branch directly to the controller. It receives a validated project directory and launches asynchronously without `waitFor`:

- Windows: `cmd.exe /c start "" cmd.exe /K cd /d "<path>"`, preserving a visible command prompt in the selected directory.
- macOS: `open -a Terminal <path>`.
- Linux: try `xdg-terminal-exec --working-directory <path>` first, then known installed terminals (`gnome-terminal`, `konsole`, `xfce4-terminal`) with their working-directory option; return a clear error if none can launch.

Arguments are passed as `ProcessBuilder` elements whenever possible; Windows' shell command is constructed only from a quoted/escaped absolute path. This preserves spaces and avoids treating a project path as a command.

### 3. Define “Build from/to here” as an execution-order range

Each project Build control becomes a compact menu with three explicit actions: **Only this module**, **Build from here**, and **Build through here**. `from` selects the clicked project and every following project; `through` selects the first project through the clicked project. Both boundaries are inclusive and are calculated from `getProjectsForWorkspace(id, true)`, not by table DOM order. The endpoint confirms that the target project belongs to the requested workspace and accepts a constrained mode enum, then delegates the selected ordered list to the existing sequential builder. This retains disabled-project skipping, stop-on-failure, WebSocket logs, summaries, and retry behavior.

### 4. Export paths as a distinct, intentionally simple format

`GET /workspaces/{id}/export-paths` returns `text/plain; charset=UTF-8`, one normalized absolute project path per line in execution order. It does not include workspace metadata, headings, artifact IDs, excludes, or a trailing synthetic record. Its filename is derived safely from the workspace name and visibly distinguishes it from the full workspace export.

## Risks / Trade-offs

- **Browser-only logs disappear after refresh:** intentional non-persistence; the action is available while the build session is on screen.
- **Linux terminal availability varies:** use the freedesktop launcher plus fallbacks and surface a non-success response instead of silently doing nothing.
- **Range meaning can be misread:** menu labels state the direction; confirmation/copy describes inclusive execution-order bounds.
- **Incorrect workspace/project pairing could run unrelated modules:** validate ownership before selecting the range.

## Testing Strategy

- Unit-test range selection for only/from/through modes, boundaries, sort order, disabled projects, and wrong-workspace rejection.
- Unit-test the paths export payload and attachment headers with mixed absolute/relative stored path data.
- Unit-test command selection/argument construction for Windows, macOS, Linux launcher/fallback/no-launcher conditions.
- Exercise the workspace UI manually with a multi-project workspace: streamed logs download accurately, terminal opens in the module, all three build choices select expected modules, and paths download contains only ordered paths.
