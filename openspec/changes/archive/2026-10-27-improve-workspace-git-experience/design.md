# Design

## Context

The Git view is a standalone page (`workspace-git.html`) driven by one vanilla-JS IIFE (`workspace-git.js`, about 480 lines) that calls the JSON endpoints in `WorkspaceGitController`. Unlike the rest of the app, it does not use HTMX. Git runs through `GitCommandRunner`: process-per-command, bounded output, timeouts. One fair `Semaphore(read-workers=8)` covers every command, remote ones included. Mutations serialize per Git common directory in `GitMutationCoordinator`. Operations are in-memory `Job`s that the client polls every 350 ms.

Measured hot spots in the current code:
- `discovery.repository()` runs 3 `rev-parse` processes on every request.
- `GitChangeService.status()` SHA-256 hashes every changed file.
- `diff()` calls `status()` 2–3 times and runs about 12–18 git processes.
- Discovery walks into `target/` and `node_modules/` and re-runs on every page entry.
- The client re-renders the whole sidebar for each status response and refreshes every repository on each window focus.

The legacy branch selector (`GitService.listReferences/checkoutReference`) takes a `File` directory, but its endpoints and fragment are bound to Maven project IDs. Frontend libraries are vendored from pinned npm tarballs via `frontend/vendor-lock.json` + `scripts/sync-vendor.py`. STOMP over SockJS already exists at `/ws-logs` with a `/topic` simple broker.

## Goals / Non-Goals

**Goals:**
- Fix the performance problems above without weakening the existing snapshot and staleness guarantees.
- Add every feature in the specs while keeping each part independently shippable: tasks are grouped so phases can be merged separately.
- Keep the page framework-free (vanilla ES modules), consistent with the current Git view.

**Non-Goals:**
- A merge or conflict editor; conflicts still go to an external editor.
- Force push, interactive rebase, cherry-pick, or arbitrary history rewriting beyond amending the last unpushed commit.
- Automatic background fetch.
- File-system watchers. Focus-based and interval-gated refresh is enough for now.
- Migrating the Git view to HTMX. Its stateful, fine-grained client interactions (line selection, drafts, cancellation) do not fit fragment swaps.

## Decisions

### D1. Split local and remote command capacity
`GitCommandRunner.run(..., remote=true, ...)` acquires a separate `remote` semaphore (`workspace.git.remote-workers`, default 4). Local commands use the existing `read-workers` semaphore, raised to `max(8, cores*2)`.
- *Alternative:* an unbounded local semaphore. Rejected: hundreds of simultaneous `git status` processes thrash the disk.
- *Alternative:* a priority queue. Rejected as more complex with no clear gain.

### D2. Cache resolved repositories, revalidate cheaply
`WorkspaceGitDiscoveryService.repository()` returns the cached `Resolved` after a cheap check instead of re-running 3 `rev-parse` processes. The check is:
- the root still exists and is not a symlink;
- the root is still under the canonical base;
- the `gitDir` `fileKey` is unchanged.

A full `resolve()` runs only when the check fails. Any mismatch → `GitProblem.stale()` as today.

`GitMutationCoordinator` gets an overload keyed by `Resolved.commonDir()`, which removes its extra `rev-parse` process. The `File` overload stays for legacy callers.

### D3. Two status shapes
- `GET .../status` (listing) returns branch, ahead/behind, files and a **light version**: a SHA-256 of the raw porcelain output, `HEAD`, the index file's size and mtime, and each changed path's size and mtime. No file contents are read.
- `diff()` keeps returning the **full** content-hash version. File-level and line-level actions (stage, unstage, discard file, selected-file commit, edit) validate against it under the mutation lock, exactly as today.
- Repository-level destructive actions (stash, discard-all, unstage-all) capture their snapshot just before the confirmation opens, via `GET .../status?full=true`, which returns the content-hash version for the targets. The server validates that full version under the lock, so a same-size, same-mtime edit can never be discarded silently. Non-destructive actions (fetch, pull, push) need no version.
- `stagedVersion` (HEAD + index hash) is unchanged.
- *Alternative:* keep full hashing but cache by mtime. Rejected: it is still O(changed bytes) on cold start and makes the semantics harder to reason about.
- `--untracked-files=all` → `normal`. An untracked directory shows as one entry with `kind: "untracked-dir"`. Expanding it calls `GET .../untracked?dir=` (the same porcelain call, scoped with `all`).

### D4. Cheaper diff construction
The diff builds `status` once, reuses `head` once, and caches `core.autocrlf` per repository with the config file's mtime as the key. It reads both blobs with one `git cat-file --batch` process. The coherence check (status before vs after) remains, but compares light versions plus the hashes of the two compared files only.

### D5. Discovery exclusions and persisted result
- `workspace.git.scan-exclude` holds directory names (default `target,node_modules,build,dist,out,.idea,.gradle,.mvn`). These directories return `SKIP_SUBTREE` and are not counted as visits.
- The last `Discovery` per workspace is kept in memory, keyed by `(workspaceId, canonical basePath)`. `GET /repositories` returns a completed `Operation` immediately with the cached result when one exists. `?refresh=true` forces a scan.
- Persisting the result to H2 across restarts is out of scope. Instead, the spec covers automatic client rediscovery on a 404 for an unknown identity.
- *Alternative:* store the result in a new JPA entity. Rejected: identities include `fileKey` and must be revalidated anyway, so persistence saves little.

### D6. Operation progress over STOMP
`GitRepositoryActionService.Job.outcome()` publishes `Operation` to `/topic/workspace-git/{workspaceId}` through `SimpMessagingTemplate`. The client subscribes with the vendored `sockjs` + `stompjs` and resolves `watch()` from messages. As a fallback, if no message arrives within 2 s, or the socket fails, it polls every 1 s. A 404 while polling → outcome `uncertain` + refresh.
- *Alternative:* SSE. Rejected: STOMP is already configured and vendored.

### D7. Client architecture
`workspace-git.js` is split into ordered classic `defer` scripts under `static/js/workspace-git/`. They share one `window.WorkspaceGitApp` namespace. ES modules are not used because `src/test/frontend/workspace_git_regression.py` inlines the scripts into a `file://` page, where module imports are blocked. The harness gains a single ordered file list, which the template also uses.

| Module | Responsibility |
|---|---|
| `api.js` | `request()` with `AbortController`, rediscovery-on-404 for reads |
| `state.js` | Page state |
| `sidebar.js` | Per-repository renderer: `renderRepository(id)` updates one section; a `scheduleRender()` rAF batch handles global counts |
| `diff.js` | Rendering, context expansion, highlighting hook |
| `commit.js` | Modes, Commit & Push, amend |
| `operations.js` | STOMP + fallback |
| `layout.js` | Splitters |
| `shortcuts.js` | Keyboard shortcuts |
| `sync.js` | Synchronization overview |
| `history.js` | Commit history |
| `stash.js` | Stash list |
| `editor.js` | File editing |

`workspace-git.js` becomes the last-loaded bootstrap. `window.WorkspaceGit` stays exported for the Python regression tests.
- Loading state: the diff container gets `.is-loading` (dimmed, `pointer-events:none`). A spinner overlay appears after a 150 ms delay, cleared on render or error. The error state includes a Retry button. Clicking a file in another repository calls `chooseRepository(id, {reconcile:false})`.

### D8. Layout splitters
Splitters use CSS custom properties on `.git-layout` (`--git-sidebar-width`, `--git-commit-height`, `--git-split-ratio`):
- the grid becomes `var(--git-sidebar-width) 6px minmax(0,1fr)`;
- the workbench becomes rows `auto auto auto minmax(0,1fr) 6px var(--git-commit-height)`.

Handles are `role="separator"` elements driven by pointer capture and arrow keys (step 16 px, Shift for 64 px). Values are clamped and stored in `localStorage` under `mvnbuilder.git.layout`. Handles are hidden under 700 px.

### D9. Syntax highlighting with highlight.js
Vendor `@highlightjs/cdn-assets` (core build plus the language files listed in the spec).
- **Language** comes from a filename map, so `pom.xml` and `*.xml` → xml, and `Dockerfile` is handled too.
- **Multi-line constructs:** the client highlights each change block's old-side text and new-side text as two strings, then splits the HTML by line, closing and reopening open `<span>`s at each newline. This handles block comments that start inside the block. A comment that started before the visible context can still be mis-coloured; expanding to the full file (D11) fixes that, since the full file is then highlighted as one string.
- **Safety:** highlight.js escapes input and its output is assigned via `innerHTML`. No other content is inserted as HTML.
- **Word diff:** a client-side token LCS on paired `-`/`+` lines (pairing already exists in split rendering), capped at 500 tokens per line; it emits `<mark class="git-word">`.
- **Large diffs:** highlighting is skipped above `workspace.git.highlight-max-lines` (default 5000, sent in the page model).
- *Alternative:* Prism. It is similar, but highlight.js has better auto-escaping and a maintained CDN-assets package.
- *Alternative:* Shiki. Rejected as too heavy (WASM).

### D10. Editing with CodeMirror 5
Vendor `codemirror@5.65.x` (`lib/codemirror.js/.css` plus modes: clike, javascript, xml, yaml, properties, sql, css, htmlmixed, shell, markdown). CodeMirror 5 ships ready-to-use single files that fit `sync-vendor.py`. CodeMirror 6 needs a bundler. Monaco is several MB and duplicates the staging UI.

The backend adds two endpoints:
- `GET .../file?path=` returns `{content, hash, eol, bom, finalNewline, editable, reason}`.
- `PUT .../file` takes `{path, content, hash}`.

The write runs inside `GitMutationCoordinator`:
1. `safePath`, `fileHash == hash`, size limit, text check, and the filter/working-tree-encoding check, reusing the `content()` attribute logic.
2. Normalize the client's `\n` content back to the original EOL, BOM and final-newline state.
3. Write to `<file>.mvnbuilder-tmp`, then `ATOMIC_MOVE`.
4. Preserve POSIX permissions where supported.

The editor replaces the diff pane while open, with Save, Cancel and an unsaved-changes guard that reuses `ConfirmationModal`.

### D11. Context expansion
`GET .../diff?...&context=all` returns lines computed with `--unified=<large>` (the full file) under the same `version`. The client requests it only for "Show full file". Incremental "expand gap" uses the same full-file response, cached client-side per `version`, and shows the hidden ranges. If the version differs, the expansion is rejected as stale. Git's edit script does not depend on the context size, so the n-th changed line of the full output is the n-th changed line of the normal output. The server therefore gives changed lines in the full view the **same IDs and blocks** as the normal view; unchanged lines get their own IDs (`c…`) and block -1. Selections made in either view are valid staging selections without any server-side mapping.

### D12. Commit & Push, Publish, rebase pull, untracked deletion
- `Commit` and `SelectedCommit` gain `push: boolean`. In the worker: commit → if `push`, check the upstream → `git push` (remote=true). The outcome message is composed: `success` = "Committed <sha>; pushed", `failed` with message "Committed <sha> locally; push failed: …". The draft is cleared because the commit exists.
- New actions in `ACTIONS`:
  - `publish`, with `remote` in the request; validated against `git remote`; runs `push -u <remote> HEAD`.
  - `pull-rebase`, which needs `confirmation="confirmed"` and runs `pull --rebase` on an existing upstream. A conflict → `failed` with a message about rebase in progress.
  - `delete-untracked`, with a path, which revalidates: the path is in a fresh status as `?`, it is not a directory containing `.git`, and `safePath` passes. It is then deleted with `Files.delete`; a directory is deleted only when its `kind` is `untracked-dir`, recursively, without following links.

### D13. Synchronization overview
`GitSyncService.summary(workspace)` runs per repository on the local pool:
- `git for-each-ref --format=%(refname:short)%00%(upstream:short)%00%(upstream:track,nobracket)%00%(HEAD) refs/heads`
- the light status count;
- the mtime of `FETCH_HEAD`.

It returns all repositories in one response: `GET /workspaces/{id}/git/sync`. The bulk buttons reuse `/actions` with computed targets. The stale-fetch age is `workspace.git.stale-fetch-hours` (default 24). The UI is a modal or panel built with the existing glass/indigo styling.

### D14. History, amend, stash, branch switching, editor shortcut
- **History:** `GET .../log?skip=&limit=30` uses `git log --format=%H%x00%h%x00%s%x00%an%x00%at -z`. "Pushed" means `git merge-base --is-ancestor <sha> @{u}`, computed once as `rev-list @{u}..HEAD`. `GET .../commits/{sha}/files` and `.../commits/{sha}/diff?path=` reuse the line parser with `<sha>^` vs `<sha>` blobs (empty tree for root).
- **Amend:** `Commit.amend=true` plus `Commit.head`. The commit is rejected if HEAD ≠ head, the HEAD commit is in the upstream, or a merge marker exists. It runs `commit --amend --file=-`.
- **Stash:** `GET .../stashes` uses `git stash list --format=%gd%x00%H%x00%gs%x00%ct`. Mutations `stash-apply|stash-pop|stash-drop` carry `{ref, sha}`. The server checks that `stash@{n}` still resolves to `sha`, otherwise returns stale, then runs with `refs/stash@{n}`.
- **Branches:**
  - New repository-scoped endpoints in `WorkspaceGitController` call the existing `GitService.listReferences/checkoutReference/createBranch` with `Resolved.root()`, inside `GitMutationCoordinator`, and return JSON.
  - The Git view renders its own picker in the existing `ConfirmationModal` style instead of the project-bound Thymeleaf fragment.
  - After a checkout, `MavenProject.gitBranch` is updated for registered projects under that root, so the Projects page stays consistent.
- **Open in VS Code:** `POST .../repositories/{id}/open-editor` → `DesktopLauncherService.openVsCode(root)`.

## Risks / Trade-offs

- [The light version could miss a same-size, same-mtime edit] → Every mutation recomputes the full content version under the lock before writing, so correctness is preserved. Only the listing may lag until the next refresh.
- [Highlighting per change block can mis-colour constructs opened before the visible context] → This is accepted. Full-file view gives exact colouring, and colours never affect staging.
- [CodeMirror 5 is in maintenance mode] → It is stable and has a small surface. The editor is isolated in `editor.js`, so a later swap to CodeMirror 6 or Monaco is local.
- [Discovery exclusions can hide a real repository inside `build/` or `out/`] → The list is configurable, and the defaults are documented in `docs/workspace-git.md`.
- [STOMP broadcasts can reach other tabs of the same workspace] → Messages contain only the outcome data the polling endpoint already exposes to the same workspace. The client ignores unknown operation IDs.
- [Rebase pull and amend rewrite local history] → Both need explicit confirmation, amend is blocked for pushed commits, and force push is never offered.
- [A larger client surface adds regression risk] → Each module ships with its tasks and frontend regression checks, and the phases can be merged separately.

## Migration Plan

- There is no data migration. New configuration properties have defaults.
- Rollout follows the task groups: performance/feedback → layout/highlighting → actions (Commit & Push, publish, rebase, delete) → sync overview → history/amend/stash/branches → editing.
- Rollback means reverting the commit for a group. The endpoints are additive, and the `status` response stays backwards-compatible: `version` is kept, and the light `version` replaces the old one transparently for clients.

## Open Questions

- Exact default for `workspace.git.stale-fetch-hours` (24 h is assumed); it can be tuned without changing the specs.
- Whether to offer a dark theme for highlighting. It follows the app theme once the app has one.
