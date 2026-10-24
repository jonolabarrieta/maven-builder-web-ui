## Why

Workspaces currently register only Maven projects, the Windows terminal shortcut opens Command Prompt, and a failed build can only restart at the failing project. Users need to manage other project folders, open PowerShell, and explicitly continue past a failure without permanently disabling a project.

## What Changes

- Keep workspace creation and automatic refresh Maven-only. Add an explicit, unchecked option in Add projects to register selected folders without a POM.
- Persist a project kind and preserve manually added non-Maven entries across refresh, ordering, restart, and workspace export/import. Show their folder names and available Git/local shortcuts; exclude Maven-only controls and execution.
- Open PowerShell on Windows in the selected project directory, preferring PowerShell 7 when installed and falling back to Windows PowerShell. Preserve Linux/macOS terminal behavior and report launch errors.
- Keep Retry from here and add Skip and continue after sequential build failure. Resume the original pending queue without rebuilding completed projects; skipping is local to that continuation and never changes the project's enabled flag.
- Validate workspace ownership and continuation state, prevent duplicate submission, and retain accurate failure, skipped-project, and build-history feedback.

## Capabilities

### New Capabilities

- `manual-workspace-projects`: Explicit registration, persistence, presentation, and Maven execution boundaries for non-Maven folders.

### Modified Capabilities

- `project-folder-browse`: Opt-in manual addition of folders without a POM through the existing project explorer.
- `project-local-shortcuts`: PowerShell terminal sessions on Windows and shortcuts for registered non-Maven folders.
- `workspace-import-export`: Preserve explicitly registered non-Maven entries in exported/imported workspaces while reading existing Maven-only files.
- `workspace-build-actions`: Retry or skip the failed project and continue the original build queue with scoped validation and feedback.

## Impact

Changes affect project persistence and migration, WorkspaceService/import-export/hierarchy and ordering, project explorer and detail templates, Maven-only endpoints, DesktopLauncherService, build failure/continuation orchestration, notifications/history, and their tests. Keep Java 21, Spring Boot, Thymeleaf/HTMX, and bundled frontend assets. No new build-system adapters, automatic non-Maven discovery, shell execution feature, or Git-view discovery change is included.
