## Why

Workspace Git controls currently act on registered Maven projects and cannot review file changes or prepare commits. A dedicated Git view will let users review, stage, commit, and synchronize every repository beneath the workspace base folder, including repositories without Maven projects.

## What Changes

- Add a Git view alongside the workspace Projects view, preserving the existing interface language and styling.
- Discover repositories recursively beneath the base folder, including nested repositories, Git submodules, and linked worktrees; group Maven modules sharing a repository.
- Show branch/upstream information, ahead/behind counts, and unstaged, staged, untracked, and conflicted files.
- Compare local file changes side by side or as a unified diff, distinguishing index-to-working-tree from HEAD-to-index comparisons.
- Stage and unstage whole files, change blocks, or selected text lines, with stale-diff detection.
- Keep staged commits and add selected-file commits for one or several repositories, using one message and independent outcomes; preserve excluded staged/unstaged files.
- Provide repository and selected-repository fetch, pull, push, stash, unstage-all, and confirmed discard actions, with individual progress and outcomes.
- Add file checkboxes, collapsible repository/file groups, and a visible Push selected action; retain selections, folding, diff position, and drafts on refresh.
- Exclude commit-history/branch comparison, a merge editor, force push, and deletion of untracked files from this change.

## Capabilities

### New Capabilities

- `workspace-git-discovery`: Discover and identify repositories independently of Maven registration and build selection.
- `workspace-git-view`: Workspace navigation, repository/file selection, live status, and accessible operation feedback.
- `git-file-diff`: Accurate local comparisons, unified/split presentation, and explicit unsupported-content states.
- `git-selective-staging`: Whole-file and partial staging/unstaging with repository-scoped validation and snapshot checks.
- `git-repository-actions`: Repository commits, synchronization, stash, and discard with bounded concurrency and per-repository results.

### Modified Capabilities

None. Existing project-scoped actions and their selection, skipped-project, and confirmation contracts remain in place.

## Impact

Add workspace-scoped Git controllers, services, response models, templates, and JavaScript. Extend the Git command boundary for exact output, patch input, timeouts, and repository coordination. Reuse application dialogs and operation feedback infrastructure. Keep the Spring Boot/Thymeleaf/HTMX stack, bundled frontend assets, and workspace persistence format; no Maven-project records are required for discovered repositories.
