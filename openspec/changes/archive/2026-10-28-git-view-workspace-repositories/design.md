# Design

## Context

See proposal.md for the reasons behind this change. Current state that shapes the approach:

- **Discovery.** `WorkspaceGitDiscoveryService.discover` walks `basePath` looking for `.git` entries. `resolve()` rejects any root outside the base. The repository identity hashes `workspaceId + base + root + gitDir + fileKey`. `last()` reuses the cached result while the base is unchanged.
- **Workspace projects.** `WorkspaceGroupService.composition(id).all()` already returns local and parent-imported projects. `BuildService.gitTargets` already maps projects to Git roots, but it skips disabled projects. `MavenProject.absolutePath` can be null for older rows; the fallback is `basePath + relativePath`.
- **Sync.** `GitSyncService` returns correct data, including `status.branch()`. The wrong-branch symptom is caused by `sync.js`, which renders the repository name on the first branch row, sorted alphabetically.
- **Commit.** Two endpoints exist. `/repositories/{id}/commit` covers the staged commit and amend. `/commits` (`GitSelectedCommitService`) builds a temporary index from HEAD containing only the selected files and preserves the real index. Only the second one is kept.
- **Launcher and history.** `DesktopLauncherService.openExplorer` already exists. The `/log`, `/commits/{sha}/files` and `/commits/{sha}/diff` endpoints back the current history panel.

## Goals / Non-Goals

**Goals:**
- One source of truth for which repositories exist: the registered projects.
- Keep the existing per-request validation model, but base it on membership.
- Make every download and path action client-side where the data is already present.

**Non-Goals:**
- Keeping operation history across page reloads or server restarts.
- Merge or rebase conclusion, and amend, inside the Git view.
- Changing the Projects page's own Git status column (`MavenProject.gitBranch`).

## Decisions

### 1. Project-based discovery behind a small provider

`WorkspaceGitDiscoveryService` gets a `WorkspaceGitProjects` dependency, a small interface implemented in the `service` package on top of `WorkspaceGroupService.composition(id).all()`. It returns `(projectName, folder)` pairs for every project, enabled or not. This keeps `service.git` free of a dependency cycle with the workspace services.

The discovery flow:

```
projects --> folder (absolutePath | base+relativePath)
         --> git -C folder rev-parse --show-toplevel   (bounded concurrency)
         --> canonical root --> dedupe --> Resolved
         failures --> warning naming the project
```

- **Alternative: keep the base walk and filter it by project roots.** Rejected because it still misses projects outside the base and pays for the full walk.
- **Alternative: reuse `BuildService.gitTargets`.** Rejected because it skips disabled projects, uses `java.io.File` heuristics and lives in the build layer. Its root-finding logic can be shared later.

The base-folder walk, the exclusion list (`workspace.git.scan-exclude`) and the scan limit are removed. The `scan-directory-limit` property becomes unused and is documented as removed.

### 2. Identity and membership validation

- **Identity:** `hash(workspaceId + root + gitDir + fileKey)`. The base is dropped, so changing the base folder no longer invalidates repositories that are still registered.
- **Membership:** discovery stores a **project fingerprint**, a hash of the sorted `(projectId, folder)` pairs.
- **`repository(workspace, id)`:**
  1. Recompute the fingerprint. This is one in-memory database query and starts no Git process.
  2. If the fingerprint differs, reject with `stale()`. The client already rediscovers on that error. Otherwise, run the existing `unchanged()` filesystem revalidation without the `startsWith(base)` check.
- `last()` returns the cached result only while the fingerprint matches. This covers both "project removed" and "reopen the view".

Rejected alternative: re-running membership resolution per request with `rev-parse`. It costs one process per request on the hot status/diff path.

### 3. Descriptor carries the absolute path

`Repository(id, name, path)` becomes `Repository(id, name, displayName, absolutePath)`:

- `displayName` is computed server-side after deduplication. It is the folder name, extended with the shortest parent segments needed to make it unique.
- The relative `path` field disappears. Before, it was relative to the base, which no longer bounds the repositories.
- `SyncRepository` mirrors these fields.

The server computes `displayName` so that the sidebar, sync and operation history agree.

### 4. Sync rendering

The server keeps returning flat data and sorts the branches with the current one first. The client renders a header row per repository from `branch` and the current branch's counts, followed by collapsible child rows for the other branches.

The filters (status, repository text and branch text) apply first, and a header is rendered whenever any of its branches survives the filters. The bulk targets are computed from the visible headers.

For the dialog:

- It uses `resize: both` with `min-width`/`min-height` and `max-width: 98vw`/`max-height: 95vh`.
- A `ResizeObserver` stores the size in `localStorage` (`git.sync.size`).

Rejected alternative: a custom drag handle. The native resize is enough and keyboard users can still use the default large size.

### 5. Single commit path with confirmation

- **Removed:**
  - The scope `<select>` and `state.commitMode` (`setCommitMode`, the staged branch in `submitCommit`).
  - The amend UI (`loadAmend`, `setAmend`, `#git-amend-row`).
  - The `/repositories/{id}/commit` and `/repositories/{id}/amend` endpoints, along with the service methods and models that only they use.
- **New flow:** `submitCommit` builds `commitTargets()`, then opens a confirmation through the existing reusable confirmation dialog (`dialogs.js`). The dialog shows the repository `displayName`, its files with their area, and a "then push" line when applicable. Only after confirmation does `submitCommit` call `/commits` with the snapshots captured when the dialog opened. The keyboard shortcuts (`Ctrl+Enter`, `Ctrl+Shift+Enter`) open the same dialog.
- **Repository checkbox:**
  - It becomes tri-state, derived from file marks.
  - Toggling it marks or unmarks every eligible file in all areas. When a path is in both Changes and Staged Changes, it prefers the Changes entry, which is the working-tree content.
  - A clean repository keeps a plain, explicit checkbox for bulk actions.
  - Bulk targets are repositories that are checked or mixed, plus clean repositories that were checked explicitly.
  - Repositories whose status has not loaded yet mark their files once the status arrives.

### 6. Header utilities

- **Copy path:** `navigator.clipboard.writeText(absolutePath)`. If clipboard access is denied, the path is shown in a read-only field inside a small dialog.
- **Open folder:** `POST /repositories/{id}/open-explorer`, which resolves the identity and calls `launcher.openExplorer(root)`. It mirrors `open-editor`.
- **View log:** `history.js` is refactored so its commit list takes its own elements and a file handler. The History panel and the new `#git-log-dialog` both use it. Selecting a file in the log opens a read-only unified comparison inside the dialog. That comparison has its own small renderer, because `renderDiff` is bound to the main pane's state (selection, staging, context gaps) and reusing it would replace the user's current comparison.
- **Downloads:** a shared helper `download(name, lines)` builds a `Blob`. File names follow `<workspace>-<what>-YYYYMMDD-HHmm.txt`. Changed repositories come from the latest statuses held in `state`.

### 7. Operation history

`state.operations` is extended with client metadata `{ action, label, startedAt, targets }`, set where `watch()` is called (commit, actions, sync bulk).

`renderOperations` becomes a history list:

- It shows all entries of the session, newest first, with a cap of 200 to bound memory.
- Each entry has a per-entry download, and the panel has a Download all control.
- The panel replaces "Operation results" in place, under the same `<details>`.

## Risks / Trade-offs

- **[A repository not tied to any project disappears from the view]** → The empty state and the release notes explain that repositories come from workspace projects. Users add a Folder project to bring a repository back.
- **[Merges, rebases or reverts can no longer be concluded from the view]** → The refusal message points to Open in VS Code. Conflict listing and staging stay available.
- **[Projects outside the base widen the reachable filesystem]** → Only registered project folders resolve. Client paths are still never accepted, and `safePath` stays scoped to each root.
- **[Fingerprint check on every request adds a database read]** → The read uses the composition already cached per request where available. Measure it in `GitPerformanceTest` and cache it for a short time if needed.
- **[Folder projects with a null `absolutePath`]** → Use the same fallback as `gitTargets`. If neither path exists, emit a warning.

## Migration Plan

No data migration is needed. Discovery caches are in memory and old identities become unknown, which triggers the existing automatic rediscovery. Rollback means reverting the release, since nothing is persisted. The scope selector and amend removal go in the release notes as breaking UI changes.
