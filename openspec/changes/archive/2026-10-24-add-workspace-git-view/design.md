## Context

The application uses Java 21, Spring Boot, Thymeleaf, HTMX, and locally bundled frontend assets. `workspace-detail.html` contains project-oriented Git controls; `WorkspaceController` delegates fetch, pull, unstage, discard, and stash to `BuildService`/`ProcessExecutionService`. `GitService` manages references through the installed Git executable. Its current text runner joins output lines and merges stderr, which is unsuitable for exact file contents, NUL-delimited status, or patches. `WorkspaceService.findGitRoot` already recognizes `.git` files and folders but starts from Maven registrations rather than discovering all repositories.

The agreed scope includes every repository rooted at or below the workspace base folder, local staged/unstaged comparisons, selective staging, and commits per repository. Existing Maven registration, execution ordering, and skipped-project semantics must remain independent. The CSS-CDN description in OpenSpec configuration is older than the current bundled-asset implementation; follow `frontend/README.md` and `frontend/TERMINOLOGY.md` for asset generation and English UI labels.

## Goals / Non-Goals

**Goals:**
- Provide an IDE-style Git workspace with repository/file navigation and a large diff surface.
- Support repositories without a POM, nested repositories, submodules, linked worktrees, and repositories without an initial commit.
- Preserve the distinction between HEAD, index, and working-tree contents throughout review, staging, and commit.
- Keep operations responsive, repository-scoped, and observable, with precise partial-failure reporting.

**Non-Goals:**
- Commit-history or arbitrary branch comparison; editing files or providing a merge editor.
- Branch creation/checkout controls in the new view, force push, automatic merge/rebase, or deleting untracked files.
- Cloning repositories, managing credentials, or changing existing project selection contracts.
- Persisting discovered repositories as Maven projects or introducing a frontend framework.

## Decisions

### 1. Discover Git working trees independently of Maven

Introduce `WorkspaceGitDiscoveryService`. Traverse the configured base folder, including the folder itself, without following directory symlinks and without entering `.git` administrative directories. Maven scan exclusions and skipped-project flags do not exclude repositories. Continue below repository roots to find nested repositories and initialized submodules. Recognize both `.git` folders and files, and validate candidates using Git's resolved working-tree root, Git directory, and common directory. Bare repositories and uninitialized submodule directories are not editable working trees and are reported as unsupported when encountered.

Use canonical worktree roots for deduplication. Different linked worktrees remain distinct entries even when they share a common Git directory. Generate opaque workspace-scoped repository IDs from resolved roots; revalidate membership and current filesystem identity on every request. Only working-tree roots within the canonical base folder are eligible. A base folder that is merely a subdirectory of an enclosing repository does not grant access to that enclosing repository. A linked worktree inside the base folder can reference its legitimate Git metadata outside it.

Return discoveries incrementally or asynchronously, with a scan generation, warnings for unreadable paths, and explicit incomplete-scan outcomes if configured resource limits are reached. Missing/invalid base folders get an actionable empty/error state. No implicit fetch occurs on entry or discovery refresh.

**Alternative:** reuse Maven registrations or stop traversing after the first `.git`. Both omit repositories explicitly requested by the user. Following symlinks adds ambiguous workspace membership and traversal loops.

### 2. Give the Git view its own controller, state, and page

Introduce `WorkspaceGitController`, repository DTOs, `workspace-git.html`, and page-specific `workspace-git.js`. Add Projects/Git navigation to the existing workspace page and the new page. Inherit the current Indigo styling, app dialogs, typography, and bundled Tailwind pipeline.

Desktop layout: a repository/file sidebar and a diff pane with a repository toolbar and commit form. Display clean repositories as well as changed ones, with text search and an optional changed-only filter. Each repository exposes Changes (tracked unstaged and untracked), Staged Changes, and Conflicts. Commit drafts belong to individual repositories. On narrower screens, select repository/file first and show the diff with an explicit back control; offer unified presentation when two columns are impractical. Keep all actions keyboard reachable, with labelled selections and announcements for progress/errors.

Store repository selection, the `(repository, path, area)` file selection, comparison mode, scroll position, and per-repository commit drafts in page state. Initial discovery and bounded status reads load in the background. Load diff contents only for the selected file. Refresh on action completion, explicit refresh, and return to a visible/focused page, coalescing duplicate refresh events. Reject responses with superseded generations; refreshing status must not replace focused controls or the commit input. If a selected entry disappears, explain why and choose a predictable adjacent entry or empty state. Warn through the existing app dialog before leaving the page with non-empty commit drafts.

Proposed workspace-scoped HTTP surface, all beneath `/workspaces/{workspaceId}/git`:

| Method/path | Responsibility |
| --- | --- |
| `GET /` | Render Git page and workspace navigation context. |
| `GET /repositories` | Discover/re-discover repository descriptors and scan warnings. |
| `GET /repositories/{repositoryId}/status` | Return branch/upstream, cached-remote ahead/behind, file groups, capabilities, and state version. |
| `GET /repositories/{repositoryId}/diff?path=...&area=...` | Return a structured local comparison and selection tokens. |
| `POST /repositories/{repositoryId}/stage` | Stage a file or a selected block/line set against its snapshot. |
| `POST /repositories/{repositoryId}/unstage` | Unstage a file, selected block/line set, or all staged changes. |
| `POST /repositories/{repositoryId}/commit` | Start one repository commit with a message and expected staged snapshot. |
| `POST /actions` | Start an allowlisted action for selected repositories, optionally one file for discard. |
| `GET /operations/{operationId}` | Poll workspace-owned discovery/action progress and per-repository results. |

Discovery can return a running operation ID for a long scan. Async results remain available for the page session and a bounded retention period. Expected failures use structured messages and meaningful HTTP responses; stale mutations return 409 and include refresh guidance. No endpoint accepts an arbitrary repository path or executable command from the client.

**Alternative:** extend the existing project controller and reuse project IDs. This would couple non-Maven repositories to project registration and make repository membership and deduplication harder to enforce.

### 3. Extend the Git boundary for machine-readable status and exact diffs

Add an exact-output runner behind `GitService` or a small dedicated `GitCommandRunner`, retaining the installed Git executable. Support byte-preserving stdout, separate stderr, byte input through stdin, bounded output, cancellation/timeouts, and argument arrays without a shell. Configure machine-readable commands to disable color, external diff programs, and text-conversion filters. Do not pass file contents through line-joining log readers. Keep logs separate from response payloads and render paths, code, and errors as text.

Read status with `status --porcelain=v2 -z --branch` and parse both index and working-tree states, renames, conflicts, untracked paths, and unborn HEAD. Ahead/behind reflects locally cached remote refs, with an explicit no-upstream/unknown state. Preserve original path identities independently of display labels. Handle spaces, tabs, quotes, Unicode, and newlines in supported paths; never split filenames on whitespace.

For Changes, compare index to working tree. For Staged Changes, compare HEAD to index. Use an empty-tree base for unborn HEAD and an empty file for an untracked text file. Return old/new contents, line numbers, hunks, and metadata from one coherent snapshot; retry a bounded read if its inputs change during construction. Additions, deletions, and renames retain their real identities. Binary, oversized, unsupported-encoding, symlink, mode-only, gitlink, and conflicted entries get explicit metadata states. Text size/output limits are documented configuration with a visible reason when reached, never silent truncation.

Implement a lightweight bundled diff renderer using Git-produced changes; no code editor runtime is required. Split/unified modes share the same structured model and selectable added/deleted lines. Large supported text diffs use chunked rendering to avoid blocking the page.

**Alternative:** adopt a full editor or a second Git library. That adds runtime/dependency complexity, especially for the native package, before editable code or history browsing is in scope.

### 4. Build selected index content on the server and validate the displayed snapshot

Introduce `GitChangeService` for status/diff and staging. A diff response includes repository/path/area identity, HEAD/index/content identities, applicable metadata, and stable hunk/line selection identifiers. Requests carry these tokens and selection identifiers, not arbitrary patch text. Re-read and validate the relevant versions inside the mutation coordinator before constructing the selected index content server-side. A changed HEAD, index entry, file content, or path returns 409 without applying the requested selection.

Support staging and unstaging individual additions, deletions, replacement selections, complete hunks, and complete files. Preserve unselected edits and exact newline/EOF behavior. For a partially staged file, each area has an independent comparison. Construct the staged blob from the base plus selected changes; for partial unstage reconstruct HEAD plus the changes that remain selected for staging. Use Git-generated hunk coordinates and explicit addition/deletion pairing to preserve mixed replacements and EOF markers. This avoids patch-header and empty-file ambiguities while keeping the same index-only selection contract. New text files can be partially staged from an empty base. Unstaging a partially added file preserves its full working-tree contents, and removing its last staged content removes the added index entry appropriately. Use an isolated temporary-index construction where necessary to obtain correct unborn-HEAD and empty-file semantics, then publish with Git's index-lock discipline and current-index validation; clean temporary artifacts on failure. Never use whole-repository reset to implement a file/line action.

Whole-file actions handle additions, modifications, deletions, and rename metadata atomically and use literal, repository-relative path arguments. Partial actions are disabled with an explanation for binary/oversized/unsupported text, symlink/mode-only/gitlink changes, rename pairs, and unresolved conflict entries; whole-file actions remain available where Git supports them. Users can explicitly stage a resolved conflict file as a whole; the view does not invent a resolution. Validate all requested paths and selections, reject traversal and administrative paths, and never follow symlinks to read arbitrary files outside the repository.

**Alternative:** run interactive `git add -p` or accept client-generated patches. Interactive parsing is brittle for a web UI; trusting client patches bypasses the server's selection and path contract. File-only staging does not meet the agreed requirement.

### 5. Coordinate actions by repository and retain existing project behavior

Introduce `GitRepositoryActionService` with bounded worker capacity and operation IDs/results, plus an application-wide mutation coordinator keyed conservatively by canonical Git common directory. This serializes index/HEAD/remote mutations for a working tree and its linked worktrees while allowing independent repositories to run concurrently. Discovery/status reads are bounded separately and do not monopolize mutation workers. Route existing project-scoped Git mutation entry points through this same coordinator, preserving their commands, selection rules, logs, and confirmations. Git's native locks still protect interactions with external tools; report lock contention and refresh rather than deleting another tool's locks.

Repository checkboxes are independent of Maven build checkboxes. Normalize duplicate IDs in a bulk request, validate every target against the workspace, and report progress/success/failure individually. Commit is always single-repository and requires a non-blank message, staged changes, no unresolved conflicts, and the expected staged snapshot. It never stages additional files, uses `-a`, disables hooks, retries a commit automatically, or pushes implicitly. Preserve drafts after failures; on success clear only the submitted draft if the user has not edited it while the request was running.

Use the existing Git/SSH configuration and non-interactive bounded execution. Fetch uses the repository's normal configuration. Pull uses `--ff-only`; divergence produces actionable feedback rather than an automatic merge/rebase. Push is a normal non-forced push using the configured upstream; missing remote/upstream gets setup guidance rather than silently choosing a destination. Remote operations have configurable, longer deadlines than local status reads. A timeout or interrupted remote command is reported as uncertain until local state is refreshed; do not automatically retry a potentially completed push or commit.

Stash uses `git stash push`, with an unchecked include-untracked option controlling `-u`. Discard restores only tracked unstaged paths from the index and requires the reusable confirmation dialog naming repositories/files and irreversibility. Repository-wide discard requires typing the workspace name, consistent with the existing safeguard; one-file discard uses a standard confirmation. Staged contents and untracked files are preserved. A stale discard snapshot requires refreshing and confirming again. Unstage-all only changes the index, including in an unborn repository. Bulk actions do not roll back repositories that have already succeeded.

**Alternative:** reuse existing unbounded project-level parallelism without a shared coordinator. Multiple Maven modules could mutate the same repository concurrently, and the new Git view could race the existing controls. A single global queue avoids that race but blocks unrelated repositories unnecessarily.

## Risks / Trade-offs

- Large base folders → asynchronous bounded discovery, `.git` pruning, incremental progress, explicit warnings, and selectable-file-only diff loading.
- External editor/Git activity → coherent snapshot reads, optimistic validation, native locks, stale-response rejection, and refresh after terminal outcomes. App coordination cannot prevent unrelated external processes from changing a repository.
- Partial-patch corner cases → real temporary Git fixtures covering mixed selections, CRLF, missing final newline, new/deleted files, renames, and unborn HEAD; reject unsupported selections explicitly.
- Linked worktrees share references → conservative common-directory serialization costs some parallelism but prevents conflicting app-originated mutations.
- Credentials, hooks, signing, or remote failures → preserve native configuration, bound command duration, report useful stderr, and keep drafts/selection available.
- Conflicts and special files → explicit states, whole-file capabilities, and external resolution guidance; no misleading editable diff or partial-stage control.
- Existing project action regression → keep existing API/UI contracts and add tests that overlapping new/old actions share the coordinator.

## Migration Plan

No database migration is required. Ship the new route/assets and navigation with the shared Git runner/coordinator, then enable discovery and review before exposing mutations during implementation. Rebuild bundled CSS with `python3 scripts/build-css.py` after template/class changes. Validate existing project Git controls alongside the new view. Rollback removes the Git navigation/route and new assets; user-created commits and working-tree changes remain ordinary Git state and are not reverted.

## Open Questions

No product decision blocks implementation. Implementation must choose documented numeric limits for scan concurrency, response/file sizes, local/remote timeouts, and operation retention, and verify them against representative large workspaces. These limits must produce explicit UI states rather than narrowing repository discovery silently.

## Selected-file and multi-repository commit extension

Retain the existing single-repository staged commit endpoint. Add `POST /workspaces/{workspaceId}/git/commits` accepting a common message and targets containing repository ID, expected status version, and selected `(path, area)` entries. Validate all identities and selections before scheduling, then revalidate each target inside the shared coordinator. Produce one commit per repository and independent outcomes. Multi-repository commits are not atomic.

For selected-file commits, hold the real index lock and construct a private index from HEAD (empty for unborn HEAD), copying chosen index entries for Staged Changes and adding chosen working-tree paths for Changes. Include both paths of rename pairs. Commit using the private index, then reconcile only selected entries into a copy of the original real index and atomically publish it. Preserve unselected index entries and working-tree bytes; report uncertain outcomes if a commit succeeds but index publication fails. Keep configured hooks/signing and never auto-retry. Disallow selected-file commits during unfinished merge/rebase/cherry-pick/revert operations.

Whole-file staging updates exact tracked paths with `git add -u`, avoiding explicit `git add -A` rejection of ignored parent directories. If a selected Changes path is tracked only in the real index, copy that exact entry into the private index before updating its working-tree version. New untracked paths retain normal ignore checks; no force-add or ignore-rule changes are used.

The sidebar gains file checkboxes, per-group selection, and separate repository/group disclosure controls. Only one source per path can be selected. File selections survive repository switches and are pruned only if their original area/path disappears. Collapse state is page-scoped and independent of selection and diff navigation. The commit form exposes staged/current, selected/current, and selected/multiple modes with one shared batch draft for the multiple mode. Repository checkboxes remain independent; a visible Push selected button removes the need to find that action inside a menu.
