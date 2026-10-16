## Why

The bulk "Git changes" menu only offers Unstage and Discard. Setting local work aside across several projects currently forces users out of the app to run `git stash` per repository. A bulk Stash action, with an explicit choice about untracked files, keeps work recoverable and fits the existing bulk workflow.

## What Changes

- Add a **Stash selected** entry to the bulk "Git changes" menu on the workspace detail page, next to Unstage selected and Discard changes.
- Clicking it opens the reusable confirmation modal, extended with an optional checkbox so the user chooses whether to include untracked files. The choice maps to `git stash push -u` (include untracked) or `git stash push` (tracked changes only).
- Add a `stash` bulk action handled by `POST /workspaces/{id}/bulk-action` with an `includeUntracked` flag. It runs stash concurrently on the selected, enabled projects and emits a `git-stash` action summary to the build output, like Unstage/Discard.
- Extend the reusable dialog with an optional single checkbox option whose checked state is passed to the confirm callback, without changing existing dialog behavior.
- Remove or wire the currently unused `confirmUnstage`/`confirmDiscard` leftovers only if touched; no behavior change to Unstage/Discard.

## Capabilities

### New Capabilities

- `git-stash`: Bulk-stash local changes for selected workspace projects, letting the user choose whether untracked files are included.

### Modified Capabilities

- `confirmation-modal`: The reusable dialog gains an optional checkbox option whose state is available to the confirmation callback.

## Impact

- `workspace-detail.html`: bulk "Git changes" menu, `executeBulkAction`, `_executeBulkActionRequest`, and the `gitActionTitle` map for the `git-stash` summary.
- `confirmation-modal.js`: optional checkbox support (label, default, returned value).
- `WorkspaceController.bulkAction`: new `stash` case plus an `includeUntracked` request parameter.
- `BuildService`: new `bulkGitStash(projects, includeUntracked)` using `ProcessExecutionService`, mirroring `bulkGitUnstage`.
- Tests: bulk-action controller test and a frontend dialog/action regression.
