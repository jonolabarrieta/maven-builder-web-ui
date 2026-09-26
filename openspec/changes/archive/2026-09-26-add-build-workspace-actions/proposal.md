## Why

MvnBuilder already shows build output and exposes per-project Explorer, VS Code, and Build actions, but common local-development tasks still require copying data elsewhere: saving a build log, opening a shell in a module, building a contiguous portion of the configured build order, and extracting only the workspace paths. These operations should be available directly in the workspace view.

## What Changes

- Add a download action for the currently displayed build-log session as a UTF-8 `.txt` file.
- Add an **Open Terminal** action beside the existing project-local Explorer and VS Code actions, opening at the selected module directory on Windows, macOS, and Linux.
- Replace the single per-module Build action with a build-range chooser: build only that module, build from that module through the end of the configured order, or build from the beginning through that module. Ranges are inclusive and retain the existing sequential failure/disabled-project behavior.
- Add **Export Workspace Paths**, downloading a UTF-8 `.txt` containing only project paths in current execution order.

## Capabilities

### New Capabilities

- `workspace-build-actions`: Download the current build output and run a sequential, inclusive range of workspace projects from a module-level action.

### Modified Capabilities

- `project-local-shortcuts`: Open a terminal in a project directory across supported desktop operating systems.
- `workspace-import-export`: Export only the ordered project paths of a workspace.

## Impact

- `workspace-detail.html` gains the log-download, terminal, range-choice, and path-export UI and client-side log serialization.
- `WorkspaceController` gains terminal, range-build, and paths-export endpoints.
- `BuildService` gains a range-selection entry point while reusing the current sequential build pipeline.
- Controller/service tests cover ranges, downloads, path ordering, and OS command selection/failure reporting.
