# Verification

## Automated (2026-10-28)

- `mvn test`: 192 tests, 0 failures, 0 errors, 1 skipped (optional benchmark). Two tests were added afterwards to `WorkspaceGroupsIntegrationTest` for registered, disabled, imported and legacy project folders; that class passes on its own (19 tests).
- `python3 src/test/frontend/workspace_git_regression.py`: 182 browser checks, all passing.
- `python3 scripts/build-css.py --check`: `app.css` is current.
- `openspec validate git-view-workspace-repositories --strict`: valid.

## Manual checklist (pending)

Run the application on a workspace that has an enabled project, a disabled project, a folder project, a project imported from a parent workspace, and a project outside the base folder. Then check:

1. **Repository list**
   - Every project's repository is listed by folder name, with no path text.
   - The branch label is visible and the tooltip shows the absolute path.
   - An unregistered repository under the base folder is not listed.
2. **Removing a project**
   - Remove a project in Projects and return to Git: its repository is gone without pressing Refresh repositories.
3. **Repository checkbox**
   - Checking a repository marks all of its files.
   - Unmarking one file shows the mixed state.
4. **Commit and Commit & Push**
   - Both show the confirmation with every repository and file.
   - Cancel changes nothing.
   - Confirm commits only the marked files; staged but unmarked files stay staged.
5. **Header actions**
   - Copy path copies the absolute path.
   - Open folder opens the system file explorer.
   - View log shows commits and a read-only comparison.
6. **Downloads**
   - Download changed paths contains the absolute paths of the repositories with changes.
   - Operation history entries and Download all paths contain the expected absolute paths.
7. **Synchronization**
   - Each repository row shows the branch that is actually checked out, including a repository whose current branch is not alphabetically first.
   - The repository and branch filters work.
   - The window resizes, and the size persists after a reload.
