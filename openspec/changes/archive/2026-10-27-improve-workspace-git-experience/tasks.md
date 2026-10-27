# Tasks

## 1. Backend responsiveness

- [x] 1.1 In `GitCommandRunner`, add a separate `remote` semaphore (`workspace.git.remote-workers`, default 4) used when `remote=true`, and raise the local default to `max(8, cores*2)`. Verify with a new `GitCommandRunnerTest` in which a blocked remote command saturating its permits does not delay a local `git --version`.
- [x] 1.2 In `WorkspaceGitDiscoveryService.repository()`, return the cached `Resolved` after a cheap revalidation (exists, not a symlink, under base, unchanged `gitDir` fileKey) and fall back to `resolve()` only on mismatch. Verify that `WorkspaceGitTest` still passes and that a new test counts zero `rev-parse` invocations on a repeat call (spy runner).
- [x] 1.3 Add a `GitMutationCoordinator.mutate(Path commonDir, Supplier)` overload and switch the Git-view callers (`GitChangeService.select`, `GitRepositoryActionService`, `GitSelectedCommitService`) to `Resolved.commonDir()`. Verify that the existing coordinator tests and `WorkspaceGitControllerTest` pass.
- [x] 1.4 Implement the light status version (porcelain hash + HEAD + index size/mtime + per-path size/mtime, no content reads) and switch to `--untracked-files=normal` with a `kind: "untracked-dir"` entry. Keep `stagedVersion` unchanged. Verify with a test that listing a repository with a 50 MB untracked file does not open it (spy `fileHash`) and that the version changes when a file's mtime changes.
- [x] 1.5 Add `full=true` support to `GET .../status`, returning the content-hash version, and make stash, discard-all and unstage-all validate the full version under the lock. Verify with a test that an edit made after the snapshot, with identical size and restored mtime, is rejected as stale.
- [x] 1.6 Add `GET .../repositories/{id}/untracked?dir=` to the controller, listing the files inside an untracked directory. Verify with a controller test that covers a traversal rejection.
- [x] 1.7 Refactor `GitChangeService.diff()`: compute one status per attempt, read `head` once, cache `core.autocrlf` per repository (keyed by config mtime), read blobs through a single `cat-file --batch` process, and keep the full version in the response. Verify that `WorkspaceGitTest` diff/CRLF/rename tests pass and that a spy shows ≤ 7 git processes per diff.
- [x] 1.8 Add discovery exclusions (`workspace.git.scan-exclude` with the default list) returning `SKIP_SUBTREE` without counting visits. Verify with a test where a repository under `target/` is not discovered and the `visited` count excludes the 1,000 directories of a `node_modules` fixture.
- [x] 1.9 Cache the last `Discovery` per `(workspaceId, basePath)` and make `GET /repositories` return a completed operation from the cache unless `?refresh=true`. Verify with a controller test: the second call performs no walk, `refresh=true` walks, and a changed base path walks.
- [x] 1.10 Publish `Job` outcome changes to `/topic/workspace-git/{workspaceId}` through `SimpMessagingTemplate`. Verify with a unit test (mock template) that each outcome transition sends a message containing the operation.

## 2. Frontend restructure and responsiveness

- [x] 2.1 Split `workspace-git.js` into ordered classic scripts under `static/js/workspace-git/` (`state`, `api`, `sidebar`, `diff`, `commit`, `operations`, then bootstrap `workspace-git.js`) with no behavior change. Update `workspace-git.html` script tags and make `workspace_git_regression.py` inline the same ordered list. Verify that `python3 src/test/frontend/workspace_git_regression.py` passes unchanged.
- [x] 2.2 In `api.js`, add an `AbortController` per comparison request and cancel the previous one on file/repository change. Verify with a regression check that three rapid selections render only the last file.
- [x] 2.3 Fix the double diff load: on a file click in another repository, call `chooseRepository(id, {reconcile:false})`. Verify with a regression check that exactly one diff request is made for that file.
- [x] 2.4 Add the comparison loading state: `.is-loading` dimming, a spinner overlay after 150 ms, an immediate file-name update, an error state with Retry, and CSS in `workspace-git.css`. Verify with a regression check using a held diff response that the spinner is shown, the old lines are non-interactive, and Retry reloads.
- [x] 2.5 Make sidebar rendering incremental: `renderRepository(id)` updates one section, an rAF-batched `scheduleRender()` handles counts and toolbar, and search is debounced by 150 ms. Verify with a regression check (100 stub repositories) that each status arrival touches only its own section (MutationObserver count).
- [x] 2.6 Throttle the focus refresh: refresh the active repository immediately and the others only when older than `workspace.git.refresh-interval-seconds` (default 60, exposed via `data-` attribute in the template), staggered. Verify with a regression check that an immediate focus after a refresh issues one status request.
- [x] 2.7 Subscribe to `/topic/workspace-git/{id}` via the vendored SockJS/STOMP in `operations.js`. Fall back to 1 s polling after 2 s without messages or on socket failure, and map a 404 to `uncertain` + refresh. Add vendor script tags to `workspace-git.html`. Verify with regression checks for the push path (stubbed STOMP), the fallback path and the 404 path.
- [x] 2.8 On a 404 "Repository is not available" for read requests, run discovery once and retry the read. For mutations, rediscover and show a review message. Verify with regression checks for both paths.

## 3. Layout

- [x] 3.1 Add separator elements to `workspace-git.html` and the CSS variables/grid changes to `workspace-git.css` (sidebar width, commit height, split ratio; hidden below 700 px). Verify the visual layout at desktop and mobile widths in Chrome.
- [x] 3.2 Implement `layout.js`: pointer-capture dragging, arrow and Shift+arrow keys, clamping, `aria-valuenow` updates and `localStorage` persistence. Verify with a regression check that a keyboard resize changes and persists `--git-sidebar-width` across a reload of the harness.
- [x] 3.3 Add a collapse/restore sidebar control and shortcut that restores the previous width. Verify with a regression check.

## 4. Syntax highlighting

- [x] 4.1 Add `@highlightjs/cdn-assets` (core + java, json, xml, yaml, properties, sql, javascript, typescript, css, bash, markdown) to `frontend/vendor-lock.json` with integrity, run `scripts/sync-vendor.py`, and add the scripts and a theme CSS to `workspace-git.html`. Verify that the files are restored under `static/vendor/` and load without console errors.
- [x] 4.2 Implement the filename→language map and per-block highlighting with HTML line splitting (closing and reopening spans) in `diff.js`, for both split and unified modes. Verify with regression checks covering a Java block comment across lines, a file containing `<script>` that renders literally, and an unknown extension rendering as plain text.
- [x] 4.3 Implement word-level marking for paired `-`/`+` lines (token LCS, 500-token cap). Verify with a regression check that a single renamed identifier is the only `<mark>` on each line.
- [x] 4.4 Pass `workspace.git.highlight-max-lines` (default 5000) via the template and skip highlighting above it with a notice. Verify with a regression check using a 6,000-line stub.

## 5. Expandable context

- [x] 5.1 Support `context=all` on `GET .../diff` (full-file lines, same version), with changed lines keeping the IDs and blocks of the normal view so `stage`/`unstage` accept selections from either view. Verify with service tests: lines staged from the full view produce the same index as from the normal view.
- [x] 5.2 Add "Expand" gap rows and a "Show full file" toggle in `diff.js`, caching the full response per version and rejecting a mismatched version as stale. Verify with regression checks that line selections survive expansion and that a version mismatch shows the refresh message.

## 6. Repository actions

- [x] 6.1 Add `push` to the `Commit` and `SelectedCommit` records and implement commit-then-push in the same job with composed outcomes (including the commit sha) in `GitRepositoryActionService` and `GitSelectedCommitService`. Verify with tests against a local bare remote: success, push rejected after commit (commit kept, outcome names the sha), and no upstream.
- [x] 6.2 Add the `publish` action (`remote` field validated against `git remote`, `push -u <remote> HEAD`) and `GET .../repositories/{id}/remotes` to the controller. Verify with tests: publish sets the upstream; no remote → 422.
- [x] 6.3 Add the `pull-rebase` action, which requires confirmation and leaves the repository in rebase state on conflict. Verify with tests for a clean rebase and a conflicting rebase (outcome failed, `rebase-merge` present).
- [x] 6.4 Add the `delete-untracked` action (path still `?` in a fresh status, not containing `.git`, recursive only for `untracked-dir`, no link following). Verify with tests covering the stale rejection when the file was staged, and a symlinked directory not being followed.
- [x] 6.5 UI: split Commit button with Commit / Commit & Push, `Ctrl+Enter` and `Ctrl+Shift+Enter`, a Publish branch dialog (remote select) shown on no-upstream outcomes and in the header, a confirmed "Pull with rebase" offer after a divergence failure, and a Delete file action for untracked entries using `ConfirmationModal`. Update `workspace-git.html`. Verify with regression checks for each control's request payload.

## 7. Synchronization overview

- [x] 7.1 Implement `GitSyncService.summary()` (`for-each-ref` with upstream track, pending change count from the light status, `FETCH_HEAD` mtime) and `GET /workspaces/{id}/git/sync` in the controller. Verify with tests on fixture repositories that are ahead, behind, diverged, without upstream, and with a non-current branch ahead.
- [x] 7.2 Add a Synchronization button and panel to `workspace-git.html` and `sync.js`: a table, the freshness notice, a stale flag (`workspace.git.stale-fetch-hours`, default 24), and filters. Verify with a regression check using a stubbed summary that rows, filters and the stale flag render.
- [x] 7.3 Wire the Fetch all, Pull behind and Push ahead bulk actions through `/actions` with computed targets, and refresh the summary after completion. Verify with a regression check that Push ahead targets only ahead repositories.

## 8. History and amend

- [x] 8.1 Add `GET .../log?skip=&limit=` (with a pushed flag), `GET .../commits/{sha}/files` and `GET .../commits/{sha}/diff?path=` (root commit uses the empty tree) to the controller and a new `GitHistoryService`. Verify with service tests including the root commit and sha validation (reject non-hex input).
- [x] 8.2 Add a History tab or panel in `workspace-git.html` and `history.js`, with load-more, commit file list and a read-only comparison without staging controls. Verify with a regression check.
- [x] 8.3 Add `amend` and `head` to `Commit`. Reject the amend when HEAD differs, when HEAD is in the upstream, when the repository is unborn, or when a merge/rebase marker exists, and run `commit --amend --file=-`. Verify with tests for each rejection and a successful amend.
- [x] 8.4 UI: an Amend checkbox prefilled with the last message, disabled with an explanation when not allowed. Verify with a regression check.

## 9. Stash management

- [x] 9.1 Add `GET .../stashes` and the `stash-apply`, `stash-pop` and `stash-drop` actions that carry `{ref, sha}`, with the server verifying that `stash@{n}` still resolves to `sha`. Verify with tests: clean pop, conflicting apply keeps the stash, and drop after a new stash with a stale ref is rejected.
- [x] 9.2 Add a stash list panel in `workspace-git.html` and `stash.js`, with a confirmed Drop. Verify with a regression check.

## 10. Branch switching and editor shortcut

- [x] 10.1 Add repository-scoped `GET .../references`, `POST .../checkout` and `POST .../branches` to `WorkspaceGitController`, delegating to `GitService` inside the coordinator and updating `MavenProject.gitBranch` for registered projects under the root. Verify with tests mirroring `WorkspaceGitReferenceTest`, plus an invalid branch name rejection.
- [x] 10.2 Add a header branch control and picker (local/remote/tags, create branch) using `ConfirmationModal` conventions. Verify with a regression check of the checkout payload and the post-checkout refresh.
- [x] 10.3 Add `POST .../repositories/{id}/open-editor` calling `DesktopLauncherService.openVsCode(root)` and a header button. Verify with a controller test using a mocked launcher.

## 11. List actions and shortcuts

- [x] 11.1 Add per-file stage/unstage buttons and group stage-all/unstage-all to the sidebar entries, reusing the stage/unstage endpoints with the diff version obtained on demand. Verify with a regression check that the file moves groups and the comparison selection is kept.
- [x] 11.2 Implement `shortcuts.js` (next/previous file, stage/unstage, next/previous block, toggle sidebar, focus message), ignored while in inputs or the editor, plus a shortcuts help dialog. Verify with regression checks, including that typing in the textarea does not trigger shortcuts.

## 12. File editing

- [x] 12.1 Implement `GET .../file?path=` (content, hash, eol, bom, finalNewline, editable+reason) and `PUT .../file` (hash check, limits, filter/encoding rejection, EOL/BOM/final-newline restoration, temp file + `ATOMIC_MOVE`, permission preservation) in the controller and a new `GitFileEditService`. Verify with tests: CRLF preserved, stale hash → 409 with the file unchanged, traversal → 403, binary → not editable.
- [x] 12.2 Add CodeMirror 5 (lib + required modes + CSS) to `frontend/vendor-lock.json` and sync it. Verify that the files are restored and load without errors.
- [x] 12.3 Implement `editor.js`: an Edit button for eligible Changes entries, the editor replacing the diff pane, Save/Cancel, reload/copy on conflict, and the unsaved-changes guard on file/repository switch and navigation. Update `workspace-git.html`. Verify with regression checks for a save payload, a 409 handling path and the guard dialog.

## 13. Documentation and final verification

- [x] 13.1 Update `docs/workspace-git.md`:
  - remove the "no file editing" statement;
  - document Commit & Push, Publish, rebase pull, untracked deletion, sync overview, history/amend, stashes, branches, editing, layout, shortcuts and highlighting;
  - document the new `workspace.git.*` properties and discovery exclusions.

  Verify that the docs mention every new property.
- [x] 13.2 Run `mvn -q test` and all `src/test/frontend/*.py` regressions and verify that they pass.
- [x] 13.3 Manual check on a workspace with 50 or more repositories: measure the time to first interactive list, file-switch latency during a bulk fetch, and the absence of whole-sidebar re-renders. Record the results in the change's verification notes.
