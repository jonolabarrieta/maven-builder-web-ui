## 1. Reusable Dialog Checkbox Option

- [x] 1.1 In `confirmation-modal.js`, add an optional `checkbox` option (label and initial checked state) to the dialog DOM, hidden by default and reset whenever a dialog settles.
- [x] 1.2 Render the checkbox below the message when the option is configured, keep it inside the existing focus trap/keyboard handling, and pass its checked state as the `onConfirm(checked)` argument.
- [x] 1.3 Verify existing dialogs that omit the option render no checkbox and behave exactly as before.

## 2. Backend Bulk Stash

- [x] 2.1 Add `bulkGitStash(List<MavenProject> projects, boolean includeUntracked)` to `BuildService`, running `git stash push` or `git stash push -u` concurrently per enabled project through `ProcessExecutionService`.
- [x] 2.2 Have `bulkGitStash` inspect each `CommandResult` exit code, populate `failedProjects`, and broadcast an `ActionSummary` with action `git-stash`.
- [x] 2.3 Add a `stash` case and an `includeUntracked` (`@RequestParam(defaultValue = "false")`) parameter to `WorkspaceController.bulkAction`, and update its Javadoc.
- [x] 2.4 Add a controller test asserting `bulk-action` with action `stash` dispatches for both `includeUntracked` values and rejects unknown actions unchanged.

## 3. Workspace Detail Bulk Action UI

- [x] 3.1 Add a "Stash selected" entry to the bulk "Git changes" menu in `workspace-detail.html`, wired like the Unstage/Discard entries.
- [x] 3.2 Implement the `stash` branch in `executeBulkAction`: open `ConfirmationModal.confirm` with a non-danger variant, a message naming the selected project count, and an unchecked "Include untracked files" checkbox; confirm passes the checked state through.
- [x] 3.3 Extend `_executeBulkActionRequest` to append an `includeUntracked` field to the FormData only when the stash action supplies it.
- [x] 3.4 Add `'git-stash': 'Git Stash'` to the `gitActionTitle` map in `workspace-detail.html` so the summary renders a proper title and message.

## 4. Tests and Verification

- [x] 4.1 Add a frontend regression covering: stash dialog opens with the untracked checkbox unchecked, confirming unchecked posts `includeUntracked=false`, confirming checked posts `includeUntracked=true`, and cancel posts nothing.
- [x] 4.2 Manually verify bulk stash with and without untracked files, confirm Skipped projects are excluded, and confirm a repository without an initial commit is reported as a failed project in the build output.
- [x] 4.3 Run the project test suite and `openspec validate add-git-stash-button`, resolving any failures.
