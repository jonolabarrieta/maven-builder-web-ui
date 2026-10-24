## 1. Git execution and repository coordination

- [x] 1.1 Add exact-byte Git stdout/stdin and separate stderr support with argument-array execution, output limits, local/remote deadlines, and non-interactive failure handling; retain existing reference-command behavior.
- [x] 1.2 Add repository descriptor, file-status, diff, snapshot, and operation-result DTOs with explicit unborn/detached/no-upstream/unsupported states.
- [x] 1.3 Add configurable bounded read/mutation workers and a mutation coordinator keyed by canonical Git common directory, with app-owned resource cleanup.
- [x] 1.4 Route existing `GitService` checkout/create/fetch mutation entry points through the coordinator without changing reference-selection contracts.
- [x] 1.5 Route existing `BuildService` single-project and bulk fetch/pull/stash/discard/unstage commands through the coordinator while retaining selection, skipped-project, logs, and summary behavior.

## 2. Repository discovery and membership

- [x] 2.1 Implement base-folder traversal including the base itself, nested repositories, `.git` files/folders, initialized submodules, and linked worktrees, without following symlinks or entering administrative folders.
- [x] 2.2 Implement canonical worktree deduplication, opaque workspace-scoped IDs, and request-time membership/path validation independent of Maven registrations and scan exclusions.
- [x] 2.3 Add asynchronous discovery generations/progress and warnings for missing folders, unreadable paths, unsupported candidates, and incomplete scans; document configurable scan limits.
- [x] 2.4 Add temporary-repository discovery tests covering non-Maven/skipped projects, shared Maven roots, nested roots, worktrees, symlinks, and moved/cross-workspace identities.

## 3. Status and local comparisons

- [x] 3.1 Implement NUL-delimited porcelain-v2 status parsing, per-area grouping, rename/conflict/untracked identities, and branch/upstream/ahead-behind metadata.
- [x] 3.2 Implement coherent HEAD-to-index and index-to-working-tree content acquisition, including empty bases for untracked files and unborn HEAD.
- [x] 3.3 Build structured text diffs with old/new paths, line numbers, hunks, stable selection identities, and snapshot versions, preserving CRLF and missing-final-newline information.
- [x] 3.4 Add explicit binary/oversized/unsupported-encoding/symlink/mode-only/gitlink/conflict responses and ensure no external diff/text-conversion or unsafe symlink reads occur.
- [x] 3.5 Add real-Git status/diff tests for partially staged files, additions/deletions/renames, special-character paths, unborn HEAD, coherent-read retries, and unsupported states.

## 4. Whole-file and selective staging

- [x] 4.1 Implement snapshot-validated whole-file stage/unstage and unstage-all, including atomic rename pairs, explicit whole-file conflict resolution, and unborn-HEAD behavior.
- [x] 4.2 Implement server-owned selection validation and construction of index-only content changes for chosen additions, deletions, replacement subsets, and hunks.
- [x] 4.3 Implement partial stage for existing/new text files, preserving unselected index contents and the working-tree bytes.
- [x] 4.4 Implement reverse partial unstage, including partially added files and removal of their final staged addition without deleting working-tree content.
- [x] 4.5 Implement temporary-index publication where needed, expected-index verification, lock handling, and cleanup; reject stale/invalid/unsupported selections with no broader fallback action.
- [x] 4.6 Verify exact index and working-tree bytes in real-Git tests for single-line selections, mixed replacements, partial new/deleted files, CRLF, EOF changes, unborn HEAD, stale snapshots, and failures.

## 5. Repository actions and progress

- [x] 5.1 Add a workspace-owned operation registry with bounded retention, discovery/action progress, per-repository results, normalized target identities, and duplicate suppression.
- [x] 5.2 Implement single-repository commit validation/execution from the expected staged snapshot, including initial commits, configured hooks/signing, and uncertain-outcome handling.
- [x] 5.3 Implement repository and selected-repository fetch, `pull --ff-only`, and non-forced push with configured-upstream checks, longer remote deadlines, and actionable errors.
- [x] 5.4 Implement repository/selected stash with the include-untracked choice and unstage-all with preserved working trees.
- [x] 5.5 Implement single-file and repository-wide tracked-unstaged discard from the index with expected-snapshot checks, preserving staged/untracked content.
- [x] 5.6 Verify commit contents, first commit, dirty worktree preservation, fast-forward/refused divergence, push rejection, stash choices, and discard boundaries using temporary repositories and local bare remotes.
- [x] 5.7 Verify shared-common-directory serialization across new/existing action paths, bounded parallel execution across independent repositories, duplicate targets, partial failures, and timeout/lock cleanup.

## 6. Workspace-scoped HTTP endpoints

- [x] 6.1 Add `WorkspaceGitController` page handler `GET /workspaces/{workspaceId}/git` with workspace context and structured application error handling.
- [x] 6.2 Add `GET /git/repositories` beneath the workspace route for synchronous/asynchronous discovery results and scan generations.
- [x] 6.3 Add `GET /git/repositories/{repositoryId}/status` with membership checks and versioned status DTOs.
- [x] 6.4 Add `GET /git/repositories/{repositoryId}/diff` with validated path/area, structured comparison, and unsupported-content outcomes.
- [x] 6.5 Add `POST /git/repositories/{repositoryId}/stage` with whole-file/selection payload validation and stale-state 409 responses.
- [x] 6.6 Add `POST /git/repositories/{repositoryId}/unstage` with whole-file/selection/all modes and equivalent membership/snapshot checks.
- [x] 6.7 Add `POST /git/repositories/{repositoryId}/commit` with message and staged-snapshot validation plus asynchronous operation identity.
- [x] 6.8 Add `POST /git/actions` with allowlisted single/bulk targets, stash options, discard scope, and validated confirmation/snapshot data.
- [x] 6.9 Add `GET /git/operations/{operationId}` with workspace ownership validation and actionable expired/unknown-operation responses.
- [x] 6.10 Add controller integration coverage for invalid IDs/paths/selections, cross-workspace access, stale mutations, operation polling, and no mutation on GET/page entry.

## 7. Navigation, repository browser, and diff UI

- [x] 7.1 Update `workspace-detail.html` with Projects/Git navigation, preserving existing project/build controls and selections.
- [x] 7.2 Create `workspace-git.html` with matching navigation, repository search/filter/selection, status/file groups, empty states, and page-specific bundled script references.
- [x] 7.3 Add repository discovery/status orchestration in `workspace-git.js`, bounded requests, generations, focus/visibility refresh, and preservation of selection, focus, drafts, and scroll state.
- [x] 7.4 Add split/unified diff markup/rendering and chunked text display, with line numbers, change-block navigation, special-content metadata states, and safe text rendering.
- [x] 7.5 Add keyboard/pointer line and block selection plus file/selection stage/unstage controls, capability explanations, and stale-selection refresh handling.
- [x] 7.6 Add repository-specific commit form and draft state; preserve newer edits on completion/failure and use application confirmation before leaving with drafts.
- [x] 7.7 Add repository/bulk action toolbar, stash checkbox dialog, standard file-discard dialog, and typed workspace-name confirmation for repository-wide discard.
- [x] 7.8 Add operation polling, per-repository progress/results, contextual loading/error feedback, and affected-status/diff refresh after terminal or uncertain outcomes.
- [x] 7.9 Complete responsive repository-to-diff navigation, unified narrow-screen mode, accessible labels/focus/live announcements, and English terminology; update responsive styles and regenerate bundled CSS.

## 8. End-to-end validation and documentation

- [x] 8.1 Add delayed-response frontend regression coverage for stable file/line selection, stale generations, draft preservation, and keyboard split/unified interactions.
- [x] 8.2 Add browser flows for non-Maven discovery, file/line staging, partial unstage, commit, selected-repository actions, and canceled/stale discard confirmations.
- [x] 8.3 Inspect desktop and narrow-screen rendering with representative clean, changed, conflicted, unsupported, loading, and partial-failure states; fix material interaction/layout defects.
- [x] 8.4 Run the Maven suite, relevant existing frontend/reference/stash/refresh regressions, resource contracts, and bundled CSS check; document measured large-workspace behavior and chosen runtime limits.
- [x] 8.5 Document Git-view comparisons, repository scope, commit/push separation, pull/upstream requirements, partial-action limitations, conflict guidance, and refresh/error behavior in the project documentation.

## 9. Selected-file commits and collapsible navigation

- [x] 9.1 Extend the scope/specifications for file selection, one/multiple repository commits, visible bulk push, and persistent collapsed lists.
- [x] 9.2 Implement snapshot-validated selected-file commits using isolated indexes, preserving unselected staging and all working-tree contents.
- [x] 9.3 Add a workspace-scoped batch commit endpoint with one message, independent repository outcomes, and request/membership validation.
- [x] 9.4 Add persistent per-repository file checkboxes, group selection, and repository/group collapse controls.
- [x] 9.5 Add explicit commit modes for current staged changes, current selected files, and selected files across repositories, retaining drafts and selections after failures.
- [x] 9.6 Expose Push selected beside repository selection, with counts, disabled/busy states, and independent outcomes.
- [x] 9.7 Verify selected commits, excluded staged/unstaged files, hook failure, batch partial failure, multiple pushes, folding, and stale requests using real Git and browser flows.
- [x] 9.8 Update the user guide and complete Maven, frontend, CSS/OpenSpec, and desktop/mobile verification.

## 10. Tracked files beneath ignored directories

- [x] 10.1 Reproduce selected-file commit and whole-file stage failures for already-tracked files beneath an ignored directory, including additions present only in the real index.
- [x] 10.2 Update only explicitly selected tracked paths using Git's tracked-file update semantics, preserving ignored untracked files and excluded staging.
- [x] 10.3 Verify the regressions and existing Git tests; document the distinction between ignored untracked files and files already tracked by Git.
