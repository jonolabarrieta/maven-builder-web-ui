# Workspace Git view

Open a workspace and choose **Git** beside **Projects**. This view lists the Git repositories of the workspace's projects: Maven and folder projects, enabled or disabled, and projects imported from parent workspaces. A project can live inside or outside the workspace base folder; the view uses the working tree that contains the project folder, so a project in a subfolder of a repository shows that repository. Projects that share a working tree appear once; distinct linked worktrees and nested repositories with their own project remain separate.

Only registered projects count. A repository under the base folder that no project belongs to is not listed, and removing a project from the workspace removes its repository from this view unless another project still belongs to it. To show a repository without a Maven project, add its folder as a folder project.

A project whose folder is missing, unreadable or not inside a Git working tree is reported as a warning naming the project; the other repositories stay usable. Bare repositories are not editable working trees.

Opening the view again shows the last discovery while the workspace's projects are unchanged; adding or removing projects runs a new one, and **Refresh repositories** always does. If the server was restarted while the page was open, or the projects changed, the page rediscovers automatically and repeats the read; a change action is not repeated and asks you to review the refreshed state first. Discovery and normal status reads never fetch from remotes.

Repositories are shown by folder name. When two repositories share a name, the parent folders needed to tell them apart are added (for example `a/api` and `b/api`); the absolute path appears as a tooltip. The current branch is shown as a label beside each repository.

## Review and prepare changes

Select a repository, then a file in **Changes**, **Staged Changes**, or **Conflicts**. Search matches repository names, current branches and changed filenames. Use **Uncommitted changes only** to list just the repositories with modified, staged or new files that are not committed; the number beside it counts them and the choice is remembered in this browser. Repositories whose status is still loading stay listed until it arrives. Repository checkboxes are independent of Maven build checkboxes; see [Commit marked files](#commit-marked-files).

The active repository's header offers **Copy path** (its absolute path; if the browser refuses clipboard access the path is shown for manual copying), **Open folder** (the system file explorer), **Open in VS Code**, **View log**, **History** and **Stashes**. **Download changed paths** in the repository list downloads a text file with the absolute paths of the repositories that currently have uncommitted changes, one per line.

| File group | Comparison |
| --- | --- |
| Changes | Staging area → working-tree file |
| Staged Changes | Last commit → staging area |
| New text file | Empty base → working-tree content |
| First commit's staged file | Empty base → staged content |

A partially staged file appears in both applicable groups. While a comparison loads, the file name changes at once, the previous comparison is dimmed and a spinner appears if loading takes noticeably long; a failed load offers **Retry**. Switch between **Side by side** and **Unified** presentation. Code is highlighted by file type (Java, JSON, XML including `pom.xml`, YAML, properties, SQL, JavaScript/TypeScript, CSS, HTML, shell, Markdown and more), and words that changed inside a modified line are marked. Comparisons above `workspace.git.highlight-max-lines` are shown as plain text. Use the gap rows to reveal hidden unchanged lines, or **Show full file**. Select individual added/deleted line checkboxes, a change block, or the whole file, then stage or unstage. Space toggles a focused checkbox; Up/Down moves between line checkboxes. Block navigation moves within the comparison. Narrow screens provide a **Repositories** back button and initially use unified presentation.

Untracked folders are listed once as **New folder**; expand them to see and select their files. The **+** and **−** buttons beside a file, and **Stage all** / **Unstage all** on group headings, stage or unstage whole files without opening them.

Ignore rules hide untracked files, not files already tracked by Git. An artifact already versioned under `target/` therefore remains visible after adding `target/` to `.gitignore`. Whole-file staging and selected-file commits can update those tracked files without adding ignored untracked neighbors. To stop tracking existing artifacts, remove them from Git's index separately and commit that removal; editing `.gitignore` alone does not stop tracking them.

Partial staging changes the Git index and preserves the complete file on disk. Partial unstage removes only the chosen staged changes while retaining the working-tree file. If another tool edits the relevant repository state, the server rejects stale selections and the page refreshes the comparison before another attempt.

Binary/oversized/unsupported text, symlinks, submodule pointers, metadata-only changes, rename pairs, and unresolved conflicts have explicit states. Use whole-file actions where supported. Files using custom Git content filters or working-tree encoding conversion use whole-file staging so Git can apply those conversions. An individual selection that would incorrectly join lines across a missing-final-newline boundary is rejected with guidance to select the complete replacement block. Ordinary LF/CRLF content and configured standard EOL normalization are supported; an existing CRLF index with automatic EOL conversion requires whole-file staging. Resolve conflicts in an external editor (**Open in VS Code** opens the repository root), then explicitly stage the resolved whole file. This view does not provide a merge editor.

### Edit a file before committing

For a text file under **Changes**, **Edit file** opens its working-tree content in an editor with syntax highlighting. **Save** (or Ctrl+S) writes the file only; nothing is staged or committed. Saving keeps the file's line-ending style and byte-order mark, and is refused when the file changed on disk after it was opened, so external edits are never overwritten: copy your edits with **Copy edits**, then reload. Binary, oversized, symlinked, conflicted and submodule entries, files with mixed line endings, and files that use Git content filters or working-tree encoding cannot be edited here. Unsaved edits are protected by a confirmation when switching files or repositories or leaving the page.

### Layout and keyboard

Drag the separators to resize the repository list, the commit area and, in side-by-side mode, the two sides of the comparison; the separators also respond to the arrow keys (Shift for larger steps). Ctrl+B or the panel button hides and restores the repository list. Sizes are remembered in this browser. When focus is not in a text field: <kbd>j</kbd>/<kbd>k</kbd> next/previous file, <kbd>s</kbd> stage or unstage the current file, <kbd>]</kbd>/<kbd>[</kbd> next/previous change block, <kbd>c</kbd> commit message, <kbd>?</kbd> shortcut list. In the commit message, Ctrl+Enter opens the commit confirmation and Ctrl+Shift+Enter the Commit & Push confirmation.

## Commit and synchronize

### Commit marked files

A commit contains exactly the files you mark, nothing else. Mark files with their checkboxes, a whole group with its group checkbox, or a whole repository with its repository checkbox, which marks all of its files at once. A repository checkbox shows a mixed state when only some of its files are marked. Marks are kept across repository switches and refreshes while the files remain available. Clicking a filename still opens its comparison.

A file marked in **Changes** contributes its current working-tree version, including new files and deletions. A file marked in **Staged Changes** contributes its staged version and keeps any additional unstaged edits. Marking one source automatically unmarks the other for that path; checking a repository marks such a file from **Changes**. Staged files that are not marked are not committed and stay staged. Rename pairs are included together.

Enter one message and choose **Commit** or **Commit & Push**. A confirmation lists every repository with marked files, its branch and its files, and says whether a push follows; nothing runs until you confirm, and cancelling keeps the message and the marks. Each repository gets its own commit with the common message. **Commit & Push** then pushes each committed branch in the same operation. If the push fails, the commit is kept and reported as committed but not pushed, naming the new commit; it is never undone or repeated. A branch without an upstream is reported with a hint to use **Publish branch**.

Successful targets clear their submitted marks; failed or uncertain targets keep marks and the message for review. A failure in one repository does not roll back commits in another. Initial commits and commits deleting all tracked files are supported. Configured hooks and signing remain active. The commit message draft is kept while this page remains open and protected by confirmation when following application navigation away; it is not kept across a browser reload.

Commits are refused while a repository has an unfinished merge, rebase, cherry-pick or revert. Conclude it outside this view, for example with **Open in VS Code** or a terminal. The view has no amend; correct a last commit with Git directly.

Commits use private indexes and native hooks/signing. If a hook stages extra paths outside the marked set, or a commit completes but index reconciliation cannot be published, the result is marked uncertain and original staging is kept; inspect the created commit before retrying. The application does not automatically undo or repeat that commit.

### History and log

**History** lists recent commits of the current branch below the toolbar, marks commits that are not pushed, and opens read-only comparisons of each commit's files in the comparison pane. **View log** shows the same commits in a large window: older commits load as you scroll or with **Load more commits**, and a file of a commit opens a read-only comparison inside the window, leaving the current comparison untouched.

### Repository actions

To publish several repositories, check their repository checkboxes and use the visible **Push selected (N)** button. Bulk actions target the repositories with marked files and the clean repositories you checked. Each target reports its own result in the operation history.

**Fetch**, **Pull**, **Push**, **Stash changes**, **Unstage all**, and **Discard changes** are available for one repository or the selected repositories. Results identify each target independently; a failed target does not undo or repeat another target's success.

- Fetch uses the configured remote; ahead/behind counts reflect locally cached remote references.
- Pull uses `--ff-only`. When local and remote commits have diverged, the page offers **Pull with rebase** after confirmation; a rebase that stops on conflicts leaves the repository in Git's rebase state for you to continue or abort.
- Push is non-forced and uses the configured upstream. A branch without an upstream shows **Publish branch**, which pushes it to a remote you choose and sets that branch as its upstream.
- The branch name in the repository header opens a switcher for local branches, remote branches and tags, with the same checkout rules as the project reference selector, and can create a new branch from HEAD.
- Stash asks for confirmation and excludes untracked files unless **Include untracked files** is checked.
- Unstage preserves working-tree files, including before the first commit.
- Discard restores tracked unstaged changes from the index. It preserves staged and untracked files. File discard asks for confirmation; repository-wide discard requires typing the workspace name. A changed confirmed snapshot requires fresh review and confirmation.
- **Delete untracked file** removes one untracked file or folder after confirmation, checking again that Git still lists it as untracked. Links are never followed and folders containing another repository are refused.
- **Operation history** lists the operations run since the page was opened, newest first, with the action, time, overall state and each repository's result. **Paths** on an entry downloads the absolute paths of its repositories; **Download all paths** downloads every entry, grouped by action and time. The history starts empty after a reload.
- **Stashes** lists the repository's stashes. **Apply** keeps the stash, **Pop** removes it after a clean apply, and **Drop** asks for confirmation. A stash applied with conflicts stays in the list. Actions are refused if the stash list changed after you chose an entry.

### Synchronization overview

**Synchronization** shows one row per repository with its folder name, its current branch as a label, that branch's upstream, commits to push, commits to pull, divergence, pending local changes and last fetch, using local data only. The repository's other local branches are listed below it when expanded. Counts reflect each repository's last fetch; repositories not fetched within `workspace.git.stale-fetch-hours` are flagged. Filter by repository name, by branch name, and by commits to push or pull, diverged branches, missing upstreams or local changes; under a status or branch filter, matching branches are shown directly. **Fetch all**, **Pull behind** and **Push ahead** act only on the current branch of the repositories the filters show, with independent results, and refresh the overview afterwards. The window can be resized from its corner, and the chosen size is remembered in this browser.

Existing Git/SSH configuration and credentials apply. Commands are non-interactive and time-bounded. Hooks, signing, unavailable credentials, lock contention, remote rejection, and timeouts produce actionable results. An interrupted or timed-out mutation can have an uncertain outcome; refresh and inspect the repository before trying again. The application never deletes another tool's Git lock or automatically retries a potentially completed commit/push.

## Execution limits and verification

Set these Spring properties in the usual application configuration or command-line arguments:

| Property | Default | Meaning |
| --- | --- | --- |
| `workspace.git.scan-workers` | 2 | Concurrent discovery operations |
| `workspace.git.read-workers` | max(8, 2 × CPUs) | Concurrent local Git commands (status, comparisons, staging); 0 means the automatic value |
| `workspace.git.remote-workers` | 4 | Concurrent remote commands and commits; they never use local read capacity |
| `workspace.git.mutation-workers` | 4 | Action workers and shared mutation concurrency |
| `workspace.git.local-timeout-seconds` | 20 | Deadline per local exact Git command |
| `workspace.git.remote-timeout-seconds` | 120 | Deadline for remote commands and commit hooks/signing |
| `workspace.git.output-limit-bytes` | 8388608 | Per-stream command-output bound; exceeding it reports an error |
| `workspace.git.file-limit-bytes` | 1048576 | Maximum bytes per text comparison side and per edited file |
| `workspace.git.refresh-interval-seconds` | 60 | Returning to the page refreshes the active repository and only repositories older than this |
| `workspace.git.highlight-max-lines` | 5000 | Larger comparisons are shown without syntax highlighting |
| `workspace.git.stale-fetch-hours` | 24 | Age after which the synchronization overview flags a repository's last fetch |
| `workspace.git.branch-workers` | 6 | Concurrent branch reads when the workspace page refreshes project branches. Each repository is read once per refresh; `1` restores sequential reads |

The `workspace.git.scan-directory-limit` and `workspace.git.scan-exclude` properties no longer exist, because discovery resolves registered project folders instead of walking the base folder.

The action queue holds 128 pending tasks and discovery queue holds 16. Operation results are retained for up to one hour, with a maximum of 256 retained jobs and the oldest completed jobs evicted under pressure. Progress is pushed to the page over the application's WebSocket; the page polls once per second only when that channel is unavailable or silent. The browser reads status for up to four repositories concurrently, updates only the repository whose status arrived, and renders comparisons in batches. Only the selected file's diff is loaded, and a superseded comparison request is cancelled.

Listing status identifies changed files by size and change times instead of reading their contents. Comparisons and every change action validate a content-based snapshot, so an edit made after you reviewed a file is detected even when its size and modification time were restored. Existing project Git controls share the repository coordinator and have a 120-second Git deadline; Maven build execution is unchanged.

Mutations sharing a canonical Git common directory serialize, including linked worktrees and the existing project controls. Independent repositories can proceed concurrently. External editors and Git clients remain independent; snapshot validation and Git's native locks detect stale state and contention.

A local integration fixture with 51 repositories registered as projects, each with four nested module trees, is measured for discovery and bounded status loading on the development host. These observations are not performance thresholds. `WorkspaceGitTest` rebuilds and measures the fixture without a machine-specific timing assertion. Real Git fixtures also verify selected-file commits from mixed staged/working sources, excluded staging, multi-repository commits/pushes, hook failures, and exact index/working-tree contents, partial replacements, CRLF/EOF handling, first commits, rename/deletion metadata, conflicts, stale requests, locks, local bare-remote synchronization, stash options, and discard boundaries.

`GitPerformanceTest` guards the number of Git processes per comparison, that listing status reads no file contents, that discovery resolves project folders without walking build folders, and that per-request membership checks start no Git process. Run `mvn test`, `python3 src/test/frontend/workspace_git_regression.py` (requires local Chrome), and `python3 scripts/build-css.py --check`. Syntax highlighting (highlight.js) and the editor (CodeMirror 5, loaded on first use) are vendored through `frontend/vendor-lock.json`. Native-image DTO reflection hints include the Git view's request/response records. Git view styling is isolated in the bundled `workspace-git.css` file; existing shared utilities remain in compiled `app.css`.
