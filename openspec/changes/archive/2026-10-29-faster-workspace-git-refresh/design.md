# Design

## Context

See proposal.md for the motivation. The current state:

- **Rescan.** `WorkspaceService.scanAndImportProjects` calls `gitService.getCurrentBranch` for every scanned Maven project and every folder project. It caches the result per Git root only within one scan.
- **Git phase.** After the rescan, and on every page load, the page calls `GET /workspaces/{id}/git-status-table`. That endpoint runs `refreshGitStatus(workspaceId)` once per source workspace (the consumer plus each imported parent). Each call loops over that workspace's top-level projects sequentially, with its own per-root cache, and saves them.
- **Branch lookup.** `GitService.getCurrentBranch` runs `getCurrentReference`, which is three `git` processes: `rev-parse HEAD^{commit}`, `symbolic-ref`, and `for-each-ref` for the upstream. A fourth (`tag --points-at`) runs when HEAD is detached. The label displayed and stored only needs the short branch name, or the detached description. Each `git` call has a 15 s timeout in `runGit`.
- **Where stored branches are used.** Only the top-level project rows show the stored `gitBranch` (`#git-branch-{id}`). Module rows do not show it. The workspace Git view reads repositories through its own discovery and does not use `gitBranch`.
- **Other callers.** Workspace import (`WorkspaceService` around line 876), add-by-path (around line 445) and group refresh (`WorkspaceGroupService.refresh`) also call `getCurrentBranch`.

## Goals / Non-Goals

**Goals:**
- No Git process during the rescan. The Git phase reads each distinct repository once, with one process in the common case and with bounded parallelism.
- Every existing caller of `getCurrentBranch` benefits automatically from the cheaper lookup.

**Non-Goals:**
- Reading `.git/HEAD` from disk without starting a process. That was option 2 in the discussion and can follow later.
- Faster filesystem traversal or POM parsing, and caching branches between requests by mtime.
- Changing import, add-by-path or group refresh beyond the cheaper lookup they inherit.

## Decisions

### 1. Drop branch lookups from the rescan, not from the Git phase
The rescan stops calling Git, for both Maven and folder projects. Existing rows keep their stored branch, and new rows have none until the Git phase fills it in.

- *Why this side:* the Git phase is required anyway. It runs on every page load, and the `workspace-refresh-progress` spec requires a separate Git step after the list is shown. Removing it instead would break that spec and would still leave page loads paying for it.
- *Alternative rejected:* keep the lookups in the rescan and skip the Git phase after an explicit refresh. That needs a client/server flag and does nothing for page loads.

### 2. One-process branch label in `GitService.getCurrentBranch`
`getCurrentBranch(dir)` runs `git symbolic-ref --quiet HEAD`:

- **Exit 0:** the output is `refs/heads/<name>`. The label is built through the existing `ReferenceState(localRef, null, List.of(), null).getLabel()`, so the 255-character truncation stays identical.
- **Exit 1:** HEAD is detached. The method falls back to the existing `getCurrentReference(dir).getLabel()`, which gives the same tag or short-commit label as today.
- **Anything else** (not a repository, timeout, missing `git`): `n/a`, logged as today.

`getCurrentReference` itself is unchanged, because checkout and the reference selector need the full state.

- *Unborn branches:* `symbolic-ref` succeeds before the first commit, so the label becomes the branch name instead of `n/a`. The spec records this deliberately.
- *Alternative rejected:* `git rev-parse --abbrev-ref HEAD`. It prints `HEAD` when detached, so a second call would still be needed, and it fails on unborn branches.
- *Alternative rejected:* `git status --porcelain=v2 --branch -uno`. It reads the index and can be slow on large repositories.

### 3. Deduplicated, parallel Git phase
`WorkspaceService.refreshGitStatus(Collection<Long> workspaceIds)` replaces the per-workspace loop. The single-id method delegates to it.

1. Load the top-level projects of every given source workspace, so one call covers the consumer and its parents.
2. Resolve each project's directory and its Git root using the existing `findGitRoot`. A project with no Git root gets `n/a` without starting a process.
3. Read the distinct roots through a shared `ThreadPoolExecutor` with a fixed size, backed by virtual threads. This follows the `GitRepositoryActionService` pattern. The size comes from `workspace.git.branch-workers`, which defaults to 6. A single shared pool bounds the total even when two pages refresh at once. Each task returns `getCurrentBranch(root)`. A task that fails or exceeds its deadline yields `n/a`.
4. Apply the labels to every project of each root and `saveAll` them once. The save stays outside the Git work, in the repository's own short transaction, matching `parallel-git-operations`.

`WorkspaceController.getGitStatusTable` calls this once with all source ids, then returns the consumer's composition roots as before. Imported parents' branches are written to the source rows, as they are today; consumer project views are never saved.

- *Alternative rejected:* a virtual thread per repository with a semaphore created per request. Two concurrent requests could then double the number of simultaneous `git` processes.
- *Alternative rejected:* a configurable default of 4. With about 170 ms per process, 6 keeps 75 repositories at roughly 2 s without saturating antivirus scanning on Windows.

### 4. Pool lifecycle and failure isolation
The executor is created once and shut down from `@PreDestroy`. Futures are collected with a per-request deadline of 15 s per batch slot (the existing `runGit` timeout already bounds each process). A rejected or interrupted task maps to `n/a`. The endpoint never fails because one repository failed, which keeps the existing "cached values are shown" path only for transport errors.

## Risks / Trade-offs

- **[Risk]** Parallel `git` processes compete with antivirus scanning on Windows. → **Mitigation:** the limit is configurable, and `workspace.git.branch-workers=1` restores sequential behavior while keeping the other two gains.
- **[Risk]** New projects briefly show an empty branch until the Git phase ends. → **Mitigation:** the refresh progress already says branches are updating, and the phase is now much shorter.
- **[Risk]** Reading branches while a checkout or other mutation runs on the same repository. → **Mitigation:** `symbolic-ref` takes no lock. A racing read returns the old or the new branch, as it can today.
- **[Trade-off]** Unborn branches now show their name instead of `n/a`. This is more accurate, but it changes behavior and is recorded in the spec.

## Migration Plan

No data or API changes. The new property is optional. Rolling back to the previous version restores the old behavior without cleanup.
