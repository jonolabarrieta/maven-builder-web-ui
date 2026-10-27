# Proposal

## Why

The workspace Git view is correct and safe, but it slows down sharply with many repositories, gives no visible feedback while a comparison loads, and makes common workflows tedious. Today the user cannot resize panes, read highlighted code, fix a file before committing, commit and push in one step, or see which repositories need a push or pull. Remote operations also starve local reads, which makes file switching appear frozen.

## What Changes

- Responsiveness: separate local and remote Git concurrency, a lightweight status for listing, cached repository resolution, per-repository rendering, a throttled focus refresh, a visible loading state, and cancellable comparisons.
- Discovery: skip heavy build and dependency folders, reuse the last discovery result on page entry, and recover automatically when the server has forgotten repository identities.
- Resizable sidebar, comparison and commit panes, a split-view ratio and a collapsible sidebar, all persisted locally.
- Syntax highlighting by file type in both diff modes, intra-line word highlighting, and expandable context.
- Editing the working-tree version of a text file from the Git view, with optimistic concurrency.
- Commit & Push for staged and selected-file commits, plus an explicit **Publish branch** for branches without an upstream.
- A Synchronization overview listing ahead, behind, divergence, upstream and last-fetch state per repository and local branch, with bulk fetch, pull and push.
- Workflow additions:
  - Stage and unstage from the file list.
  - Keyboard shortcuts.
  - Recent commit history and amend of an unpushed last commit.
  - Stash list, apply, pop and drop.
  - Branch switching from the Git header.
  - Deleting a confirmed untracked file.
  - Opening a repository in VS Code.
  - Opt-in pull with rebase.
- Operation progress moves from 350 ms polling to WebSocket push, with polling as a fallback.

## Capabilities

### New Capabilities
- `git-view-responsiveness`: loading feedback, cancellation, non-blocking local reads, and incremental rendering for large workspaces.
- `git-view-layout`: resizable and persisted panes, and a collapsible sidebar.
- `git-syntax-highlighting`: language-aware highlighting and word-level change marking in comparisons.
- `git-file-editing`: editing working-tree file content before a commit.
- `git-sync-overview`: workspace-wide push and pull status per repository and branch, with bulk synchronization.
- `git-commit-history`: recent commits and amending the last unpushed commit.
- `git-stash-management`: listing and applying stashes in the Git view.

### Modified Capabilities
- `git-repository-actions`: Commit & Push, Publish branch, opt-in rebase pull, and confirmed deletion of untracked files.
- `workspace-git-view`: throttled refresh, list-level stage and unstage, keyboard shortcuts, branch switching, and an editor shortcut.
- `workspace-git-discovery`: cached discovery on entry, excluded heavy folders, and automatic rediscovery for unknown identities.
- `git-file-diff`: expandable context beyond the default hunk context.

## Impact

- **Backend**:
  - `service/git/*`: `GitCommandRunner` gets separate local and remote limits; `GitChangeService` gets a light status, file read/write and context; `GitRepositoryActionService` gets new actions; `WorkspaceGitDiscoveryService` gets exclusions and caching.
  - New services for sync, history and stash.
  - `WorkspaceGitController` gets new endpoints.
  - STOMP topic for operations.
- **Frontend**: `workspace-git.html`, `workspace-git.css`, and `workspace-git.js`, which is split into modules.
- **Dependencies** (vendored through `frontend/vendor-lock.json`): highlight.js and CodeMirror 5.
- **Configuration**: new `workspace.git.*` properties for remote workers, discovery exclusions and the refresh interval.
- **Docs and tests**: `docs/workspace-git.md`, controller and service tests, and `src/test/frontend/workspace_git_regression.py`.
