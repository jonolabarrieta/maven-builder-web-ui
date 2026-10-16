## Context

The workspace detail page exposes bulk Git operations through a collapsible "Git changes" menu in the toolbar. Only `restore-staged` (Unstage selected) and `checkout-discard` (Discard changes) exist there today. Both post to `POST /workspaces/{id}/bulk-action`, which `WorkspaceController.bulkAction` dispatches to `BuildService.bulkGitUnstage` / `bulkGitDiscard`; those run one command per enabled project via `ProcessExecutionService` and broadcast an `ActionSummary` on `/topic/action-summary`. The build-output panel maps summary actions to a title and message.

There is no way to set local changes aside from the app. `git stash` is the natural fit, but stashing has one meaningful choice: untracked files are excluded by default, and users frequently want them included. The reusable `ConfirmationModal` supports messages and typed confirmation, but not an arbitrary option control, so the choice needs a small, reusable addition.

## Goals / Non-Goals

**Goals**

- Add a bulk **Stash selected** action to the existing "Git changes" menu.
- Let the user choose, per invocation, whether untracked files are included, through the reusable dialog.
- Reuse the existing bulk-action transport, concurrency, logging, and summary pipeline.
- Keep the stash reversible and clearly non-destructive in tone.

**Non-Goals**

- Per-project Stash buttons (current Unstage/Discard are bulk-only; stay consistent).
- Listing, applying, popping, dropping, or inspecting stashes.
- Changing Unstage/Discard behavior or storage formats.
- A `git stash save`-style message field or per-project stash messages.

## Decisions

### 1. Extend the reusable dialog with an optional checkbox

Add an optional `checkbox: { label, checked }` (and optional `name`) option to `ConfirmationModal`. When present, the dialog renders a single native checkbox below the message and passes its `checked` value to `onConfirm(checked)`. When absent, the dialog is byte-for-byte the current behavior. This keeps the choice in the shared dialog, inherits its focus trap, Escape/backdrop handling, and styling, and is reusable for future binary options.

**Alternative:** a dedicated stash modal in `workspace-detail.html`. Rejected: duplicates overlay/focus/accessibility code and diverges from the single-dialog rule in `confirmation-modal`.

**Alternative:** two menu entries ("Stash tracked" / "Stash including untracked"). Rejected per the user's choice: one entry with an in-dialog toggle is clearer.

### 2. Reuse the bulk-action endpoint and concurrency path

Add a `stash` case to `WorkspaceController.bulkAction` with a new `@RequestParam(defaultValue = "false") boolean includeUntracked`, dispatched to `BuildService.bulkGitStash(projects, includeUntracked)`. The method mirrors `bulkGitUnstage`: one `ProcessExecutionService.executeCommand` per enabled project, running `git stash push` or `git stash push -u`, then a combined `ActionSummary` with action `git-stash`.

**Alternative:** a dedicated `POST /projects/{id}/git/stash` endpoint per project. Rejected: the feature is bulk-only and the toolbar already batches through `/bulk-action`.

### 3. Report real per-project outcomes

Unlike `bulkGitUnstage`/`bulkGitDiscard`, which hardcode `success(true)`, `bulkGitStash` inspects each result's exit code and populates `failedProjects`, because stash legitimately fails on repositories without an initial commit or with unresolved states. The build output then shows which projects failed.

**Alternative:** match the existing hardcoded success. Rejected: it would silently hide failures for a brand-new repository edge case.

### 4. Non-destructive framing, untracked excluded by default

The dialog uses the non-danger confirmation variant (indigo confirm button), since `git stash` is recoverable. The untracked checkbox defaults to unchecked so untracked files stay in the working tree unless the user opts in.

## Risks / Trade-offs

- **Empty repository / no initial commit** → `git stash` fails per project; per-project exit-code reporting surfaces it instead of masking it.
- **No local changes** → `git stash push` exits 0 with "No local changes to save"; counts as success, and the output text is already streamed to the log.
- **Merging/conflicted working tree** → stash may fail; reported as a failed project for that invocation.
- **Checkbox value lost on future refactors** → cover it with a frontend regression asserting the posted `includeUntracked` value for both checked and unchecked.
- **Untracked inclusion can be surprising for build outputs** → default off plus an explicit label ("Include untracked files") makes the scope visible before confirming.
- **Parameter naming drift** between frontend form field and controller param → use the same `includeUntracked` name in both, covered by a bulk-action controller test.
