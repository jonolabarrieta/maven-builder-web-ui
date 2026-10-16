## Why

Selecting a remote branch currently runs `git checkout origin/<branch>`, leaving the repository in detached HEAD instead of activating a local tracking branch. Selecting a tag reaches its commit, but the subsequent Git refresh replaces its name with the literal `HEAD`, making a successful checkout appear incorrect. The user confirmed that local branch selection works correctly.

## What Changes

- Carry the reference type and exact identity from the Local, Remote, or Tags tab to checkout, avoiding ambiguity between identically named branches and tags.
- Activate a local tracking branch for a selected remote branch, creating it when necessary and reusing a compatible existing branch. Report conflicting local names without resetting branches or changing upstreams silently.
- Check out tags explicitly in detached HEAD and explain that state in the confirmation and current-reference display.
- Resolve the actual current Git state after checkout and during workspace refresh: branch name, exact tag information with detached status, or abbreviated commit with detached status.
- Preserve reference information across reloads and background updates, retaining targeted branch-indicator updates and existing local checkout behavior.
- Add repository, controller, and browser regression coverage for remote tracking and tag display.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `git-reference-selector`: Define typed reference checkout, remote tracking branch behavior, explicit tag detachment, and consistent current-reference identification after reload and refresh.

## Impact

- `GitService` reference listing, checkout, and current-state resolution.
- `WorkspaceController` single-project checkout and selector model; the existing Git-status response keeps its addressable string labels.
- `WorkspaceService` Git cache refresh and scan paths, plus the branch selector and workspace reference indicators.
- Focused Java and frontend regression tests. No additional runtime dependency is required. The bulk checkout request contract remains outside this change.
