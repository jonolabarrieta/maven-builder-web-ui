# Proposal

## Why

The Git view lists every repository found under the base folder, so projects removed from the workspace still appear while registered projects outside the base folder do not. The Synchronization overview places each repository name next to its alphabetically first branch, which reads as the wrong current branch. Commit has three scope modes and an amend path that make it unclear what will be committed.

## What Changes

- **BREAKING** The Git view shows the repositories of the workspace's registered projects instead of scanning the base folder. Enabled, disabled and parent-imported projects count, inside or outside the base folder. Removed projects and unregistered repositories under the base folder are no longer listed.
- Repository identity and access validation are based on project membership instead of the base-folder prefix.
- The repository list shows the folder name without a path, with the branch shown as a prominent label.
- The active repository header gets Copy absolute path, Open in file explorer and View log (modal) actions.
- New downloads: a text file with the absolute paths of repositories that have pending changes. Operation history can also be downloaded as absolute repository paths.
- The operation results panel becomes a session operation history.
- Synchronization: one header row per repository showing its current branch, with other branches beneath. Adds a repository-name filter and a branch-name filter, shows folder names without paths, and uses a larger, resizable dialog.
- **BREAKING** Removes the commit scope selector and Amend. Commit and Commit & Push always commit only the files marked in the marked repositories; staged but unmarked content is ignored and kept. Both open a confirmation dialog that lists the target repositories and files.
- Checking a repository's checkbox marks all of its files.

## Capabilities

### New Capabilities
- `git-repository-utilities`: path copy, file-explorer opening, changed-repository path download, per-repository log modal and downloadable session operation history.

### Modified Capabilities
- `workspace-git-discovery`: the repository source becomes registered workspace projects. Membership-based access replaces the base-folder prefix rule.
- `workspace-git-view`: the repository list shows names without paths and a prominent branch label. File selection replaces the commit scope modes, and the repository checkbox marks all of its files.
- `git-repository-actions`: the staged-only commit mode is removed. Commit and Commit & Push are selected-file only and confirmed.
- `git-commit-history`: Amend is removed.
- `git-sync-overview`: grouped rows with the current branch, name filters, no paths and a resizable dialog.

## Impact

- Backend:
  - `WorkspaceGitDiscoveryService`: new source and identity.
  - `GitSyncService`, `GitRepositoryActionService` (commit endpoint removal) and `WorkspaceGitController`: open-explorer endpoint and absolute paths in repository descriptors.
  - Reuses `WorkspaceGroupService.composition` and `DesktopLauncherService.openExplorer`.
- Frontend:
  - `workspace-git.html`
  - `sidebar.js`, `commit.js`, `sync.js`, `operations.js`, `history.js`, `toolbar.js`
  - `workspace-git.css`
- Tests: `WorkspaceGitTest`, `WorkspaceGitControllerTest`, `workspace_git_regression.py`.
- Docs: `docs/workspace-git.md`.
