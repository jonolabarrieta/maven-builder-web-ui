# MvnBuilder v2.3.0

Date: unreleased  
Changes since release `v2.2.0`.

This release makes the workspace Git view follow the workspace's projects, so it lists exactly the repositories you work with. It also makes commits explicit and confirmed, fixes the branch shown in the synchronization overview, and adds shortcuts to copy, open and export repository paths.

## Repositories come from the workspace's projects

- **Breaking:** the Git view now lists the repositories of the workspace's projects instead of every repository found under the base folder.
  - **What is included:** Maven and folder projects, enabled or disabled, projects imported from parent workspaces, and projects outside the base folder.
  - **What disappears:** a repository nobody registered, or whose project was removed, no longer appears.
  - **To bring one back:** add its folder as a folder project.
- Adding or removing projects refreshes the list automatically the next time the view reads a repository.
- Projects whose folder is missing or not inside a repository are reported by name.

## Clearer repository list

- Repositories are shown by folder name, without their path. When two share a name, the parent folders needed to tell them apart are added.
- The absolute path appears as a tooltip.
- The current branch is shown as a label next to each repository.
- Search also matches branch names.

## Explicit, confirmed commits

- **Breaking:** the commit scope selector is gone. A commit contains exactly the files you marked, in every repository where you marked files.
  - Staged files that are not marked are not committed and stay staged.
  - Checking a repository marks all of its files at once. Its checkbox shows a mixed state when only some files are marked.
- **Commit** and **Commit & Push** open a confirmation that lists each repository with its branch and files. Nothing runs until you confirm, and cancelling keeps your message and marks.
- **Breaking:** **Amend last commit** is removed. Commits during an unfinished merge, rebase, cherry-pick or revert are refused; conclude them with **Open in VS Code** or a terminal.

## New repository actions

- **Copy path** copies the active repository's absolute path. If the browser refuses clipboard access, the path is shown for manual copying.
- **Open folder** opens the repository in the system file explorer.
- **View log** opens the commit log in a large window. Older commits load as you scroll, and a commit's files open as a read-only comparison inside the window.
- **Download changed paths** downloads the absolute paths of the repositories with uncommitted changes.
- **Operation history** replaces Operation results. It lists the operations run since the page was opened, newest first, with each repository's result. Each entry can download the absolute paths of its repositories, and **Download all paths** exports the whole history.

## Synchronization overview

- **Fix:** each repository row now shows its current branch. Before, the repository name appeared next to the alphabetically first branch, which looked like the wrong branch was checked out.
- One row per repository, with the other branches listed below it when expanded.
- New filters by repository name and by branch name. **Fetch all**, **Pull behind** and **Push ahead** act on the repositories the filters show.
- Repositories are shown by folder name, without their path.
- The window is larger, can be resized from its corner, and remembers its size.

## Upgrading

No database changes are required.

- `workspace.git.scan-exclude` and `workspace.git.scan-directory-limit` are no longer used and can be removed from your configuration.
- The `POST …/repositories/{id}/commit` and `GET …/repositories/{id}/amend` endpoints are removed; commits use `POST …/commits`.
- A new endpoint is added: `POST …/repositories/{id}/open-explorer`.

[Compare v2.2.0 with v2.3.0](https://github.com/jonolabarrieta/maven-builder-web-ui/compare/v2.2.0...v2.3.0)
