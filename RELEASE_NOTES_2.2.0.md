# MvnBuilder v2.2.0

Date: October 27, 2026  
Changes since release `v2.1.0`.

This release makes the workspace Git view faster and more responsive with many repositories. It adds syntax highlighting, resizable panes, editing before a commit, Commit & Push, and an overview of what needs pushing or pulling.

## Faster and more responsive Git view

- Fetch, pull and push no longer slow down comparisons and status reads. Before, a bulk fetch could freeze file switching for several seconds; it now stays under a fifth of a second in our tests.
- Opening a file shows its name at once, dims the previous comparison and shows a spinner when loading takes longer. Switching files again cancels the previous load, and a failed load offers **Retry**.
- Each repository's entry in the list updates on its own as its status arrives, so the list is usable before every status has loaded.
- Repository discovery skips build and dependency folders such as `target` and `node_modules`. Reopening the Git view reuses the last scan; **Refresh repositories** scans again.
- Listing changes no longer reads file contents. Every action still checks file contents before changing anything, so edits made after review are still detected.
- Returning to the browser tab refreshes the active repository immediately and other repositories only when their status is out of date.
- Operation progress updates live instead of being checked repeatedly.
- After a server restart, the page rescans repositories automatically when it next reads one.

## Reading changes

- Syntax highlighting for Java, JSON, XML (including `pom.xml`), YAML, properties, SQL, JavaScript/TypeScript, CSS, HTML, shell, Markdown and more.
- Words that changed inside a modified line are highlighted.
- Reveal hidden unchanged lines between change blocks, or switch to **Show full file**.
- Untracked folders appear once as **New folder** and can be expanded to their files.
- Resize the repository list, the commit area and the two sides of a side-by-side comparison by dragging or with the arrow keys. Sizes are remembered. **Ctrl+B** hides or shows the repository list.

## Preparing commits

- **Edit file** opens a changed file in an editor with syntax highlighting. Saving writes the working-tree file only: nothing is staged, line endings and byte-order marks are kept, and a file changed on disk in the meantime is never overwritten. Unsaved edits are protected when switching files or leaving the page.
- **+** / **−** beside each file and **Stage all** / **Unstage all** on group headings stage and unstage whole files from the list.
- **Uncommitted changes only** lists just the repositories with changes that are not committed, with a count; the choice is remembered.
- **Delete untracked file** removes an untracked file or folder after confirmation.
- Keyboard shortcuts: `j`/`k` next/previous file, `s` stage or unstage, `]`/`[` next/previous change block, `c` commit message, `?` shortcut list. **Ctrl+Enter** commits and **Ctrl+Shift+Enter** commits and pushes.

## Commit, history and branches

- **Commit & Push** commits and pushes in one step, for staged changes or selected files in one or several repositories. If the push fails, the commit is kept and reported as not pushed; it is never undone or repeated.
- **Amend last commit** edits the last commit while it has not been pushed.
- **History** lists recent commits, marks those not yet pushed, and shows each commit's file changes.
- The branch name in the repository header opens a switcher for local branches, remote branches and tags, and can create a new branch.
- **Publish branch** pushes a branch without an upstream to a chosen remote and tracks it.
- When a fast-forward pull is impossible because the branches diverged, **Pull with rebase** is offered after confirmation.
- **Stashes** lists stashes with **Apply**, **Pop** and a confirmed **Drop**.
- **Open in VS Code** opens the repository folder.

## Synchronization overview

- **Synchronization** shows, for every repository and local branch, the commits to push and pull, diverged branches, missing upstreams and pending local changes.
- Shows when each repository was last fetched and flags old fetches, since the counts depend on it.
- Filters plus **Fetch all**, **Pull behind** and **Push ahead**, which act only on each repository's current branch.

## Upgrading

No database changes are required. The new settings are optional; their defaults are listed in the [Workspace Git guide](docs/workspace-git.md):

- `workspace.git.remote-workers`
- `workspace.git.scan-exclude`
- `workspace.git.refresh-interval-seconds`
- `workspace.git.highlight-max-lines`
- `workspace.git.stale-fetch-hours`

`workspace.git.read-workers` now defaults to an automatic value. Bulk fetch, pull and push run at most four remotes at a time by default; raise `workspace.git.remote-workers` to run more.

## Verification

- Maven package `2.2.0` built with Java 21: 186 tests, 0 failures, 0 errors, and 1 optional benchmark skipped.
- Browser regression of the Git view: 153 checks.
- Generated CSS verified.
- Measured against v2.1.0 on 60 repositories (19,176 folders):
  - The repository list appears in 0.34 s instead of 1.65 s.
  - All statuses show in 0.63 s instead of 2.15 s.
  - Comparisons during a bulk fetch with 12 workers take at most 0.17 s instead of up to 8 s.
- On 45 real repositories with GitLab remotes, all statuses show in 0.56 s instead of 0.97 s.

[Compare v2.1.0 with v2.2.0](https://github.com/jonolabarrieta/maven-builder-web-ui/compare/v2.1.0...v2.2.0)
