# Tasks

## 1. Project-based discovery (backend)

- [x] 1.1 Add the `WorkspaceGitProjects` interface in `service.git` and an implementation in `service` that lists `(projectId, name, folder)` for every local and imported project. Use `WorkspaceGroupService.composition(id).all()` with the `absolutePath`/`basePath + relativePath` fallback, and include disabled projects. Verify with a unit test covering enabled, disabled, imported and null-`absolutePath` projects.
- [x] 1.2 Replace the base-folder walk in `WorkspaceGitDiscoveryService.discover`:
  - resolve each project folder with `rev-parse --show-toplevel` under bounded concurrency
  - deduplicate canonical roots
  - emit per-project warnings for missing or non-repository folders and for bare repositories
  - remove the exclusion list and scan limit

  Verify that `WorkspaceGitTest` covers an outside-base project, a removed project, an unregistered repository under the base (not listed), modules sharing a root, and a nested repository with its own project.
- [x] 1.3 Change the identity to `workspaceId + root + gitDir + fileKey` and add the project fingerprint:
  - stored with the completed discovery
  - `last()` returns the cached result only while the fingerprint matches
  - `repository()` rejects with `stale()` on a mismatch
  - `unchanged()` drops the base check

  Verify with tests for "project removed after discovery", "reopen with unchanged projects" and "cross-workspace identity".
- [x] 1.4 Change `GitModels.Repository` to `(id, name, displayName, absolutePath)`, compute the disambiguated `displayName` after deduplication, and update every consumer of `descriptor().path()`, including the `open-editor` response. Verify with a unit test for `a/api` and `b/api` and by compiling with `mvn -q compile`.
- [x] 1.5 Update the empty and warning states in the `GET /repositories` response so they explain that repositories come from workspace projects. Verify with a `WorkspaceGitControllerTest` case for a workspace without projects.

## 2. Synchronization overview

- [x] 2.1 In `GitSyncService`, return `name`, `displayName` and `absolutePath` in `SyncRepository` and sort branches with the current one first. Verify with a test where `develop` sorts before the checked-out `master` and the current branch comes first.
- [x] 2.2 Rewrite `sync.js` rendering:
  - one header row per repository showing `displayName`, the current-branch label (or the detached/unborn state), upstream, ahead/behind counts, changes and fetch age
  - collapsible child rows for the other branches
  - no path text

  Verify with a `workspace_git_regression.py` case for "current branch not alphabetically first".
- [x] 2.3 Add repository-name and branch-name text filters to the `#git-sync-dialog` toolbar in `workspace-git.html` and combine them with the status filter in `sync.js`. Compute bulk targets from the visible headers only. Verify with a regression case filtering by `billing` and by `release`, and check that the Push ahead count follows the filters.
- [x] 2.4 Make the sync dialog large and resizable in `workspace-git.css`: `resize: both`, viewport bounds and sticky `th`. Persist the size through a `ResizeObserver` in `localStorage`. Verify manually that the size survives a reload, and with a regression check on the stored key.

## 3. Repository list and header

- [x] 3.1 In `sidebar.js`, render `displayName` instead of `repo.path`, set the absolute-path tooltip, render the branch as a prominent badge (styled in `workspace-git.css`) and update the aria-labels. Make search match the name, the branch and file paths. Verify with a regression check that no path text appears in the list and that searching by branch name works.
- [x] 3.2 Add Copy path, Open folder and View log buttons to the active repository header in `workspace-git.html` and wire them in `toolbar.js`. Copy path falls back to a read-only field when clipboard access is refused. Verify that the buttons render and that Copy path writes the absolute path (regression test with a clipboard stub).
- [x] 3.3 Add `POST /repositories/{repositoryId}/open-explorer` to `WorkspaceGitController`, resolving the identity and calling `DesktopLauncherService.openExplorer`, with a 422 response on launcher failure. Verify with a `WorkspaceGitControllerTest` case using a mocked launcher for success, failure and unknown identity.

## 4. Log modal

- [x] 4.1 Refactor `history.js` so that its rendering and paging take a container and a repository id, keeping the existing history panel working. Verify that the existing history regression cases still pass.
- [x] 4.2 Add `#git-log-dialog` to `workspace-git.html` using that renderer: commits with load-more, files per commit and a read-only comparison in the dialog. Verify with a regression case that opens the log, loads more commits and opens a file comparison without staging controls.

## 5. Commit flow

- [x] 5.1 Remove the commit scope `<select>`, the amend row and the related help text from `workspace-git.html`. In `commit.js`, remove `state.commitMode`, `setCommitMode`, the staged branch of `submitCommit`, `loadAmend` and `setAmend`. Show the marked file and repository counts on the form. Verify that the page loads without errors and that the counts update in a regression check.
- [x] 5.2 Make the repository checkbox tri-state in `sidebar.js`:
  - toggling it marks or unmarks every eligible file, preferring the Changes entry when a path is in both areas
  - it shows a mixed state when only some files are marked
  - a clean repository keeps an explicit selection
  - bulk targets include checked, mixed and explicitly checked clean repositories

  Verify with regression cases for check all, unmark one (mixed) and Push selected counts.
- [x] 5.3 Add the commit confirmation dialog through `dialogs.js`. It lists each repository's `displayName` with its marked files and areas and a "then push" line. It captures snapshots when it opens and calls `/commits` only after confirmation; cancelling keeps the draft and the selection. Route `Ctrl+Enter` and `Ctrl+Shift+Enter` in `shortcuts.js` to open the dialog. Verify with regression cases for confirm, cancel and the shortcut.
- [x] 5.4 Remove the `POST /repositories/{repositoryId}/commit` and `GET /repositories/{repositoryId}/amend` controller methods, the staged-commit and amend code in `GitRepositoryActionService`, and the models that only they use. Make the selected-file commit refuse with an explanation during an unfinished merge, rebase, cherry-pick or revert. Verify with `mvn -q test -Dtest=WorkspaceGitTest,WorkspaceGitControllerTest`, after updating the tests: staged-but-unmarked stays staged, unborn first commit, deletion of all tracked files, merge refusal.

## 6. Downloads and operation history

- [x] 6.1 Add a shared `download(name, lines)` helper and a "Download changed repositories" toolbar button in `workspace-git.html`/`toolbar.js` that writes the absolute paths of repositories with pending changes. Disable it when nothing has changed. Verify with a regression case that checks the downloaded content and the disabled state.
- [x] 6.2 Record `{ action, label, startedAt, targets }` metadata wherever operations are started (`commit.js`, `operations.js` `runAction`, `sync.js` bulk). Turn `renderOperations` into a newest-first session history capped at 200 entries, showing per-repository outcomes by `displayName`. Verify with a regression case running two actions and checking their order and outcomes.
- [x] 6.3 Add a per-entry download of target absolute paths and a Download all control to the history panel in `workspace-git.html`/`operations.js`. Verify with a regression case checking the content of both files.

## 7. Documentation and final checks

- [x] 7.1 Update `docs/workspace-git.md` and the release notes:
  - repository source
  - removed scope and amend
  - new header actions, downloads, history and sync changes
  - removed `workspace.git.scan-exclude` and `scan-directory-limit` properties

  Verify by reviewing the docs diff.
- [ ] 7.2 Run the full suite (`mvn -q test` and `python3 src/test/frontend/workspace_git_regression.py`) and check the view manually on a workspace with disabled, imported and outside-base projects. Verify that every test passes and that the manual checklist is recorded in the change.
