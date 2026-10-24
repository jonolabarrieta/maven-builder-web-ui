# Manual folders, PowerShell, and build recovery

## Add a non-Maven project

Workspace creation and Refresh discover Maven projects only. To register a folder without a POM, open **Add Project**, check **Include non-Maven folders**, and choose the folder checkbox or enter its path and choose **Add project**. The option starts unchecked each time the dialog is opened and stays selected while browsing within that dialog.

Each explicitly selected non-Maven directory becomes one **Folder** entry. Its descendants are not automatically registered as folders. It supports selection, ordering, copy path, VS Code, file explorer, terminal, removal, and Git operations when the folder belongs to a Git repository. Folder entries do not expose POM properties, local Maven repository actions, or Maven build controls. Mixed build selections omit folders and report that omission; a folder-only selection starts no Maven process.

Refresh and restart retain folder IDs, order, and state. A missing directory stays registered with an unavailable indication until repaired or removed. Removing a registration leaves its files on disk. A POM appearing later does not silently change its kind; explicitly add that path as a Maven project to promote the registration while retaining its identity. Registering a folder that contains separately registered Maven projects does not remove those projects from the Maven build roots.

Full workspace exports retain Maven paths and represent explicit folders as `NonMavenProject: <path>` in the same ordered list. Import restores those kinds and recalculates local metadata. Legacy unmarked no-POM paths remain skipped, with feedback. **Export Workspace Paths** remains one absolute path per registration; use full configuration export when you need to retain project kinds.

## Windows terminal shortcut

**Open terminal** opens a separate PowerShell session in the selected project directory. It prefers PowerShell 7 (`pwsh.exe`) and falls back to Windows PowerShell (`powershell.exe`). It does not open Command Prompt as the interactive shell. Project paths are handled literally, including spaces, apostrophes, Unicode, brackets, and shell metacharacters. Launcher failures are reported through the existing request-error UI. Linux/macOS terminals and other desktop shortcuts retain their existing behavior.

The Windows launcher is covered by tests for encoded literal paths, persistent-session arguments, executable fallback, unavailable directories, and launch failure. This implementation was verified on Linux; an actual Windows desktop session was unavailable. Visible PowerShell window behavior, lifetime, and working directory still require verification on Windows. Command-generation tests are not a substitute for that platform check.

On Linux, the default terminal launcher receives the working directory as a single `--dir=<path>` argument. GNOME, Konsole, and Xfce launchers remain alternatives when it is unavailable. This prevents the project directory being interpreted as a command to execute. Regression tests cover literal paths and the GNOME fallback; the installed Ubuntu launcher was also checked with `--print-cmd` without opening a desktop window.

## Continue after a failed build

Sequential full, selected, and range builds stop at their first failed project. The failure banner provides two choices:

| Action | Queue used after A succeeds and B fails in A, B, C, D |
| --- | --- |
| Retry from here | B, C, D |
| Skip and continue | C, D |

Skipping applies once to the stopped run. It does not disable B or change your project selection. Already completed projects are not rebuilt. The original pending order, Maven arguments, and Java home are retained even if you change workspace ordering, selection, or the active profile before continuing. Disabled projects retain their ordinary skip behavior.

The original build stays failed in history. A continuation is recorded separately and contains timings only for executed processes. Logs, summaries, and notification history identify explicitly omitted projects. A successful pending tail does not mean the skipped project succeeded. Later projects may still fail if they depend on the omitted project; no dependencies are automatically ignored or skipped. A later failure offers its own recovery action.

If skipping leaves no eligible pending projects, **No remaining projects** is shown and no empty build run is created. Recovery requests are scoped to the workspace and original run and are accepted once. Removed, reclassified, or unavailable target projects require reviewing the workspace and starting a fresh build. Recovery actions expire after one hour, after cancellation, on application restart, or when evicted from the bounded queue (128 pending recoveries).

## Verification

- Maven tests cover legacy H2 schema upgrade, mixed registrations/hierarchy/import-export, server-side Maven boundaries, PowerShell command construction/fallback, continuation order/settings, duplicate acceptance, changed/cross-workspace targets, cancellation, expiry, and history.
- `node src/test/frontend/build_recovery_regression.js` runs DOM-double unit interaction checks for recovery controls, payloads, busy/error/empty states, and unrelated or superseded failure events.
- Browser verification uses a separate application instance on port 14333 with an in-memory database and temporary projects. It covers default Maven-only scanning, explicit folder registration, mixed build selection, a real Maven failure, and successful execution of the pending project after skipping. Desktop and 390-pixel layouts are inspected separately.
- Run `TAILWINDCSS_BIN=<standalone-compiler> python3 scripts/build-css.py --check` after changing frontend classes.

No existing workspace data needs re-registration. Existing project rows receive MAVEN as the new kind's schema default. An older application binary assumes every registration has a POM; export/back up a mixed workspace and remove folder registrations before using an older binary. Project directories must remain untouched during that downgrade.
