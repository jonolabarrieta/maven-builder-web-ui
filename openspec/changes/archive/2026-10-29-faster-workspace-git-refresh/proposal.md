# Proposal

## Why

On Windows, refreshing a workspace with about 75 projects takes several minutes. A single `git` process costs about 170 ms to start there. Every refresh runs about 450 of them, one after another:

- The rescan reads each repository's branch with three `git` commands.
- The Git phase that follows reads every branch again, with the same three commands.

## What Changes

- **Rescan no longer reads Git.** The project rescan stops querying branches. The Git phase that already follows every refresh, and every page load, is the only place branches are read. Projects added by the rescan show their branch when that phase finishes.
- **One `git` per repository.** Reading a branch name uses a single `git` command instead of three. The full lookup is only used when HEAD is detached, which is rare.
- **Repositories read in parallel.** The Git phase reads repositories concurrently, with a bounded number of simultaneous `git` processes. The bound is configurable and defaults to 6.
- Each repository is read at most once per Git phase, even when several projects share it or it serves several source workspaces.
- **Minor behavior change:** a repository whose current branch has no commits yet shows its branch name instead of `n/a`.

Expected effect for 75 repositories: about 75 `git` processes run 6 at a time, instead of 450 run sequentially. That is roughly 2–3 s of Git time instead of about 77 s.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `async-git-refresh`: adds a requirement that branch lookups query each repository once, with one process in the common case, concurrently and with a bound.
- `workspace-refresh-progress`: the rescan phase no longer reads Git. Branches for refreshed projects come only from the Git phase.

## Impact

- `GitService`: a new lightweight branch-label lookup.
- `WorkspaceService`:
  - `scanAndImportProjects` drops its branch lookups.
  - `refreshGitStatus` deduplicates repositories and reads them in parallel.
- `WorkspaceController.getGitStatusTable`: one deduplicated pass across all source workspaces.
- New configuration property `workspace.git.branch-workers`, defaulting to 6.
- `WorkspaceScaleBenchmarkTest` reports the new `git` call counts.
- No database, API or UI changes.
