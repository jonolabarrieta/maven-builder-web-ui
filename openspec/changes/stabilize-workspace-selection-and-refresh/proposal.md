# Proposal

## Why

The workspace page refreshes Git branches by replacing the entire project table body. That destroys and recreates selection controls while the user may be clicking them. The current save-and-restore handler covers completed selections but cannot make an in-progress click stable. Explicit Refresh also uses normal navigation: its spinner has a two-second fallback and the subsequent Git request is intentionally invisible to that spinner. Large workspaces make these timing problems easier to encounter and expose repeated project-hierarchy work.

## What Changes

- Keep the initial workspace page responsive with cached Git branches, then update only the branch indicators when background Git lookup finishes. Show a small, non-blocking Git update status instead of rebuilding project rows.
- Own the selected project IDs outside the replaceable table DOM. Keep the count, Select All state, and bulk actions consistent when an explicit refresh changes the project list; drop IDs for projects that no longer exist.
- Make explicit Refresh a tracked two-phase operation: rescan projects, render the updated list, then refresh Git branches. Keep a clear loading state until both phases finish or an error is reported. Preserve normal-form fallback behavior.
- Measure large-workspace refresh and rendering, then remove the known repeated hierarchy work and avoid traversing excluded directories during scanning without changing which projects are imported or shown.
- Add focused regression coverage for slow background responses, selection during updates, refresh completion/error paths, and large nested workspaces.

## Capabilities

### New Capabilities

- `workspace-project-selection`: Keep workspace project selection stable across background updates and deliberate list refreshes.
- `workspace-refresh-progress`: Report the complete explicit refresh lifecycle and retain correct project results in large workspaces.

### Modified Capabilities

- `async-git-refresh`: Refresh branch indicators without replacing the project table body or interrupting its controls.

## Impact

- `workspace-detail.html` and its workspace interaction script.
- `WorkspaceController` refresh and Git-status endpoints, plus `WorkspaceService` scan and hierarchy calculations.
- Focused controller, service, and frontend interaction tests. Thymeleaf and HTMX remain the frontend stack; no new frontend framework is required.
