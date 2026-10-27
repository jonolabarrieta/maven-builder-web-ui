# Workspace Git view

Open a workspace and choose **Git** beside **Projects**. This view discovers Git working trees at or beneath the configured base folder, including repositories without Maven projects, initialized Git submodules, and linked worktrees. Maven scan exclusions and skipped/build selections do not affect this view. Repositories sharing Maven modules appear once; distinct linked worktrees remain separate.

Discovery does not follow directory symlinks or scan `.git` internals. It skips folders named in `workspace.git.scan-exclude` (by default `target`, `node_modules`, `build`, `dist`, `out`, `.idea`, `.gradle`, `.mvn`), so a repository located only inside such a folder is not listed. A repository rooted above the configured base folder is outside this view. Bare repositories are not editable working trees.

Opening the view again shows the last completed scan for the same base folder; use **Refresh repositories** after adding, removing, or moving repositories. Scan failures and incomplete scans are reported explicitly. If the server was restarted while the page was open, the page rescans automatically and repeats the read; a change action is not repeated and asks you to review the refreshed state first. Discovery and normal status reads never fetch from remotes.

## Review and prepare changes

Select a repository, then a file in **Changes**, **Staged Changes**, or **Conflicts**. Search matches repository names, relative paths, and changed filenames. Use **Uncommitted changes only** to list just the repositories with modified, staged or new files that are not committed; the number beside it counts them and the choice is remembered in this browser. Repositories whose status is still loading stay listed until it arrives. Repository checkboxes select targets for Git actions independently of Maven build checkboxes.

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

Drag the separators to resize the repository list, the commit area and, in side-by-side mode, the two sides of the comparison; the separators also respond to the arrow keys (Shift for larger steps). Ctrl+B or the panel button hides and restores the repository list. Sizes are remembered in this browser. When focus is not in a text field: <kbd>j</kbd>/<kbd>k</kbd> next/previous file, <kbd>s</kbd> stage or unstage the current file, <kbd>]</kbd>/<kbd>[</kbd> next/previous change block, <kbd>c</kbd> commit message, <kbd>?</kbd> shortcut list. In the commit message, Ctrl+Enter commits and Ctrl+Shift+Enter commits and pushes.

## Commit and synchronize

Enter a message in the selected repository's commit form. **Commit staged changes** includes the reviewed staging area only; it does not stage additional files or push. **Commit & Push** commits the same content and then pushes each committed branch in the same operation. If the push fails, the commit is kept and reported as committed but not pushed, naming the new commit; it is never undone or repeated. A branch without an upstream is reported with a hint to use **Publish branch**.

**Amend last commit** prefills the last commit's message and replaces that commit with the staged changes and the edited message. It is only available while the last commit is not contained in the upstream (amending a pushed commit would need a force push) and outside an unfinished merge, rebase, cherry-pick or revert. **History** lists recent commits of the current branch, marks commits that are not pushed, and opens read-only comparisons of each commit's files. Initial commits and commits deleting all tracked files are supported. Configured hooks and signing remain active. Non-empty drafts are kept separately per repository while this page remains open, preserved on failure, and protected by confirmation when following application navigation away. They are not persisted across a browser reload or closing the tab.

### Commit a selection of files

File checkboxes select commit content; repository checkboxes independently select targets for bulk Git operations such as push. Clicking a filename still opens its comparison. A group checkbox selects its eligible files, and the arrows beside repository names and group headings collapse/expand lists without clearing selections or the comparison. Folding and file selections survive page refresh operations and repository switches while the entries remain available.

Mark individual files in one repository or several, then choose **Commit scope**:

| Scope | Content and targets |
| --- | --- |
| Staged changes · current repository | The existing commit of all staged changes in the active repository |
| Selected files · current repository | Only checked files in the active repository |
| Selected files · multiple repositories | Only checked files across repositories, with one commit per repository and one common message |

A file checked in **Changes** contributes its current working-tree version, including new files or deletions. A file checked in **Staged Changes** contributes its staged version and keeps any additional unstaged edits. Selecting one source automatically deselects the other source for that path. Rename pairs are included together. The submit button identifies the file/repository counts. Repositories with no selected files are not included in the multiple-repository mode.

Selected-file commits preserve excluded staging and working-tree files. Successful targets clear only their submitted selections; failed or uncertain targets retain selections and the message for review. A failure in one repository does not roll back successful commits in another. Complete an unfinished merge/rebase/cherry-pick/revert before using selected-file commits; the original staged commit remains available where Git allows it.

Selected-file commits use private indexes and native hooks/signing. If a hook stages extra paths outside the selected set, or a commit completes but index reconciliation cannot be published, the result is marked uncertain and original staging is kept; inspect the created commit before retrying. The application does not automatically undo or repeat that commit.

To publish several repositories, tick their repository checkboxes and use the visible **Push selected (N)** button. File selection and commit scope do not change which repositories that button pushes. Each target reports its own result in Operation results.

**Fetch**, **Pull**, **Push**, **Stash changes**, **Unstage all**, and **Discard changes** are available for one repository or the selected repositories. Results identify each target independently; a failed target does not undo or repeat another target's success.

- Fetch uses the configured remote; ahead/behind counts reflect locally cached remote references.
- Pull uses `--ff-only`. When local and remote commits have diverged, the page offers **Pull with rebase** after confirmation; a rebase that stops on conflicts leaves the repository in Git's rebase state for you to continue or abort.
- Push is non-forced and uses the configured upstream. A branch without an upstream shows **Publish branch**, which pushes it to a remote you choose and sets that branch as its upstream.
- The branch name in the repository header opens a switcher for local branches, remote branches and tags, with the same checkout rules as the project reference selector, and can create a new branch from HEAD.
- Stash asks for confirmation and excludes untracked files unless **Include untracked files** is checked.
- Unstage preserves working-tree files, including before the first commit.
- Discard restores tracked unstaged changes from the index. It preserves staged and untracked files. File discard asks for confirmation; repository-wide discard requires typing the workspace name. A changed confirmed snapshot requires fresh review and confirmation.
- **Delete untracked file** removes one untracked file or folder after confirmation, checking again that Git still lists it as untracked. Links are never followed and folders containing another repository are refused.
- **Stashes** lists the repository's stashes. **Apply** keeps the stash, **Pop** removes it after a clean apply, and **Drop** asks for confirmation. A stash applied with conflicts stays in the list. Actions are refused if the stash list changed after you chose an entry.

### Synchronization overview

**Synchronization** lists every repository and local branch with its upstream, commits to push, commits to pull, divergence and pending local changes, using local data only. Counts reflect each repository's last fetch, which is shown; repositories not fetched within `workspace.git.stale-fetch-hours` are flagged. Filter by commits to push or pull, diverged branches, missing upstreams or local changes. **Fetch all**, **Pull behind** and **Push ahead** act only on each repository's current branch, with independent results, and refresh the overview afterwards.

Existing Git/SSH configuration and credentials apply. Commands are non-interactive and time-bounded. Hooks, signing, unavailable credentials, lock contention, remote rejection, and timeouts produce actionable results. An interrupted or timed-out mutation can have an uncertain outcome; refresh and inspect the repository before trying again. The application never deletes another tool's Git lock or automatically retries a potentially completed commit/push.

## Execution limits and verification

Set these Spring properties in the usual application configuration or command-line arguments:

| Property | Default | Meaning |
| --- | --- | --- |
| `workspace.git.scan-directory-limit` | 100000 | Maximum visited directories; hitting it marks discovery incomplete |
| `workspace.git.scan-workers` | 2 | Concurrent discovery operations |
| `workspace.git.read-workers` | max(8, 2 × CPUs) | Concurrent local Git commands (status, comparisons, staging); 0 means the automatic value |
| `workspace.git.remote-workers` | 4 | Concurrent remote commands and commits; they never use local read capacity |
| `workspace.git.mutation-workers` | 4 | Action workers and shared mutation concurrency |
| `workspace.git.local-timeout-seconds` | 20 | Deadline per local exact Git command |
| `workspace.git.remote-timeout-seconds` | 120 | Deadline for remote commands and commit hooks/signing |
| `workspace.git.output-limit-bytes` | 8388608 | Per-stream command-output bound; exceeding it reports an error |
| `workspace.git.file-limit-bytes` | 1048576 | Maximum bytes per text comparison side and per edited file |
| `workspace.git.scan-exclude` | `target,node_modules,build,dist,out,.idea,.gradle,.mvn` | Folder names skipped by discovery |
| `workspace.git.refresh-interval-seconds` | 60 | Returning to the page refreshes the active repository and only repositories older than this |
| `workspace.git.highlight-max-lines` | 5000 | Larger comparisons are shown without syntax highlighting |
| `workspace.git.stale-fetch-hours` | 24 | Age after which the synchronization overview flags a repository's last fetch |

The action queue holds 128 pending tasks and discovery queue holds 16. Operation results are retained for up to one hour, with a maximum of 256 retained jobs and the oldest completed jobs evicted under pressure. Progress is pushed to the page over the application's WebSocket; the page polls once per second only when that channel is unavailable or silent. The browser reads status for up to four repositories concurrently, updates only the repository whose status arrived, and renders comparisons in batches. Only the selected file's diff is loaded, and a superseded comparison request is cancelled.

Listing status identifies changed files by size and change times instead of reading their contents. Comparisons and every change action validate a content-based snapshot, so an edit made after you reviewed a file is detected even when its size and modification time were restored. Existing project Git controls share the repository coordinator and have a 120-second Git deadline; Maven build execution is unchanged.

Mutations sharing a canonical Git common directory serialize, including linked worktrees and the existing project controls. Independent repositories can proceed concurrently. External editors and Git clients remain independent; snapshot validation and Git's native locks detect stale state and contention.

A local integration fixture with 51 repositories, 852 directories, and four nested module trees per synthetic repository measured approximately 708 ms for discovery and 288 ms for bounded status loading on the development host. These observations are not performance thresholds. `WorkspaceGitTest` rebuilds and measures the fixture without a machine-specific timing assertion. Real Git fixtures also verify selected-file commits from mixed staged/working sources, excluded staging, multi-repository commits/pushes, hook failures, and exact index/working-tree contents, partial replacements, CRLF/EOF handling, first commits, rename/deletion metadata, conflicts, stale requests, locks, local bare-remote synchronization, stash options, and discard boundaries.

`GitPerformanceTest` guards the number of Git processes per comparison, that listing status reads no file contents, and that discovery skips excluded folders. Run `mvn test`, `python3 src/test/frontend/workspace_git_regression.py` (requires local Chrome), and `python3 scripts/build-css.py --check`. Syntax highlighting (highlight.js) and the editor (CodeMirror 5, loaded on first use) are vendored through `frontend/vendor-lock.json`. Native-image DTO reflection hints include the Git view's request/response records. Git view styling is isolated in the bundled `workspace-git.css` file; existing shared utilities remain in compiled `app.css`.
