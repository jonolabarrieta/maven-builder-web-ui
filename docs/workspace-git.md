# Workspace Git view

Open a workspace and choose **Git** beside **Projects**. This view discovers Git working trees at or beneath the configured base folder, including repositories without Maven projects, initialized Git submodules, and linked worktrees. Maven scan exclusions and skipped/build selections do not affect this view. Repositories sharing Maven modules appear once; distinct linked worktrees remain separate.

Discovery does not follow directory symlinks or scan `.git` internals. A repository rooted above the configured base folder is outside this view. Bare repositories are not editable working trees. Use **Refresh repositories** after adding, removing, or moving repositories; scan failures and incomplete scans are reported explicitly. Discovery and normal status reads never fetch from remotes.

## Review and prepare changes

Select a repository, then a file in **Changes**, **Staged Changes**, or **Conflicts**. Search matches repository names, relative paths, and changed filenames. Use **With changes only** to hide clean repositories. Repository checkboxes select targets for Git actions independently of Maven build checkboxes.

| File group | Comparison |
| --- | --- |
| Changes | Staging area → working-tree file |
| Staged Changes | Last commit → staging area |
| New text file | Empty base → working-tree content |
| First commit's staged file | Empty base → staged content |

A partially staged file appears in both applicable groups. Switch between **Side by side** and **Unified** presentation. Select individual added/deleted line checkboxes, a change block, or the whole file, then stage or unstage. Space toggles a focused checkbox; Up/Down moves between line checkboxes. Block navigation moves within the comparison. Narrow screens provide a **Repositories** back button and initially use unified presentation.

Ignore rules hide untracked files, not files already tracked by Git. An artifact already versioned under `target/` therefore remains visible after adding `target/` to `.gitignore`. Whole-file staging and selected-file commits can update those tracked files without adding ignored untracked neighbors. To stop tracking existing artifacts, remove them from Git's index separately and commit that removal; editing `.gitignore` alone does not stop tracking them.

Partial staging changes the Git index and preserves the complete file on disk. Partial unstage removes only the chosen staged changes while retaining the working-tree file. If another tool edits the relevant repository state, the server rejects stale selections and the page refreshes the comparison before another attempt.

Binary/oversized/unsupported text, symlinks, submodule pointers, metadata-only changes, rename pairs, and unresolved conflicts have explicit states. Use whole-file actions where supported. Files using custom Git content filters or working-tree encoding conversion use whole-file staging so Git can apply those conversions. An individual selection that would incorrectly join lines across a missing-final-newline boundary is rejected with guidance to select the complete replacement block. Ordinary LF/CRLF content and configured standard EOL normalization are supported; an existing CRLF index with automatic EOL conversion requires whole-file staging. Resolve conflicts in an external editor, then explicitly stage the resolved whole file. This view does not provide a merge editor, file editing, or historical/branch comparisons.

## Commit and synchronize

Enter a message in the selected repository's commit form. **Commit staged changes** includes the reviewed staging area only; it does not stage additional files or push. Initial commits and commits deleting all tracked files are supported. Configured hooks and signing remain active. Non-empty drafts are kept separately per repository while this page remains open, preserved on failure, and protected by confirmation when following application navigation away. They are not persisted across a browser reload or closing the tab.

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
- Pull uses `--ff-only`. Diverged branches require resolution using an external Git tool.
- Push is non-forced and uses the configured upstream. Configure missing remotes/upstreams externally and refresh.
- Stash asks for confirmation and excludes untracked files unless **Include untracked files** is checked.
- Unstage preserves working-tree files, including before the first commit.
- Discard restores tracked unstaged changes from the index. It preserves staged and untracked files. File discard asks for confirmation; repository-wide discard requires typing the workspace name. A changed confirmed snapshot requires fresh review and confirmation. Deleting untracked files is outside this action.

Existing Git/SSH configuration and credentials apply. Commands are non-interactive and time-bounded. Hooks, signing, unavailable credentials, lock contention, remote rejection, and timeouts produce actionable results. An interrupted or timed-out mutation can have an uncertain outcome; refresh and inspect the repository before trying again. The application never deletes another tool's Git lock or automatically retries a potentially completed commit/push.

## Execution limits and verification

Set these Spring properties in the usual application configuration or command-line arguments:

| Property | Default | Meaning |
| --- | --- | --- |
| `workspace.git.scan-directory-limit` | 100000 | Maximum visited directories; hitting it marks discovery incomplete |
| `workspace.git.scan-workers` | 2 | Concurrent discovery operations |
| `workspace.git.read-workers` | 8 | Maximum concurrent exact Git commands, including machine reads |
| `workspace.git.mutation-workers` | 4 | Action workers and shared mutation concurrency |
| `workspace.git.local-timeout-seconds` | 20 | Deadline per local exact Git command |
| `workspace.git.remote-timeout-seconds` | 120 | Deadline for remote commands and commit hooks/signing |
| `workspace.git.output-limit-bytes` | 8388608 | Per-stream command-output bound; exceeding it reports an error |
| `workspace.git.file-limit-bytes` | 1048576 | Maximum bytes per text comparison side |

The action queue holds 128 pending tasks and discovery queue holds 16. Operation results are retained for up to one hour, with a maximum of 256 retained jobs and the oldest completed jobs evicted under pressure. The browser reads status for up to four repositories concurrently and renders comparisons in batches. Only the selected file's diff is loaded. Existing project Git controls share the repository coordinator and have a 120-second Git deadline; Maven build execution is unchanged.

Mutations sharing a canonical Git common directory serialize, including linked worktrees and the existing project controls. Independent repositories can proceed concurrently. External editors and Git clients remain independent; snapshot validation and Git's native locks detect stale state and contention.

A local integration fixture with 51 repositories, 852 directories, and four nested module trees per synthetic repository measured approximately 708 ms for discovery and 288 ms for bounded status loading on the development host. These observations are not performance thresholds. `WorkspaceGitTest` rebuilds and measures the fixture without a machine-specific timing assertion. Real Git fixtures also verify selected-file commits from mixed staged/working sources, excluded staging, multi-repository commits/pushes, hook failures, and exact index/working-tree contents, partial replacements, CRLF/EOF handling, first commits, rename/deletion metadata, conflicts, stale requests, locks, local bare-remote synchronization, stash options, and discard boundaries.

Run `mvn test`, `python3 src/test/frontend/workspace_git_regression.py` (requires local Chrome), and `python3 scripts/build-css.py --check`. Native-image DTO reflection hints include the Git view's request/response records. Git view styling is isolated in the bundled `workspace-git.css` file; existing shared utilities remain in compiled `app.css`.
