## Context

The current `MavenProject` entity is also the registered workspace folder: controllers, hierarchy, ordering, shortcuts, and import/export all use its ID. `WorkspaceService.addProjectByPath` recursively discovers POMs and silently adds nothing for a folder without one. Creation/refresh scans only POMs. The Add projects explorer exposes checkboxes only for Maven folders. Import ignores listed folders without a POM.

`DesktopLauncherService.terminalCommands` currently launches `cmd.exe /K` on Windows. It already isolates platform command construction and has unit tests; Linux/macOS launchers must remain unchanged.

`BuildService` stops a sequential chain on its first failure and publishes `BuildFailure(projectName, projectId, remainingProjectIds)`. The remaining list includes the failed project. `workspace-detail.html` offers Retry from here by posting this list to the generic bulk-action endpoint. Failure events are broadcast globally and do not currently identify the workspace. Build history finalizes the failed run immediately. The new continuation must be scoped and cannot trust arbitrary client-supplied queues.

Follow the bundled CSS and English UI conventions in `frontend/README.md` and `frontend/TERMINOLOGY.md`; the older CDN description in OpenSpec configuration is not the current asset pipeline.

## Goals / Non-Goals

**Goals:**
- Explicitly register non-Maven folders without broadening automatic workspace scans.
- Preserve their identities and normal Git/local folder actions while enforcing Maven-only execution server-side.
- Open a visible PowerShell session in the selected directory on Windows.
- Offer retry or one-time skip after a failed sequential build, preserving pending order and clear outcome/history reporting.

**Non-Goals:**
- Detect or execute npm, Gradle, Python, arbitrary commands, or other build systems.
- Automatically register every folder or every Git repository, or change independent Git-view discovery scope.
- Globally disable a skipped project, ignore future failures automatically, recalculate dependencies after a skip, or persist resumable jobs across application restarts.
- Replace the project model/table throughout the application or change Linux/macOS terminal behavior.

## Decisions

### 1. Add an explicit project kind to existing registrations

Extend the existing entity with `ProjectKind` (`MAVEN`, `FOLDER`) and shared display-name/build-capability accessors. Preserve its existing table and IDs instead of renaming `MavenProject` across the application. Backfill existing rows as MAVEN; new POM parser results explicitly use MAVEN and manually registered folders use FOLDER. Folder display names use the directory basename, never invented group/artifact/version coordinates. Leave Maven metadata empty for folders and keep null-safe rendering at shared boundaries.

Deduplicate additions by canonical directory identity within the workspace, including external folders already supported by manual addition. Validate an existing, accessible directory and reject administrative Git directories and ordinary files; report actionable errors rather than silently reporting success. Existing registrations retain IDs, order, enabled state, and selection on repeated addition. A FOLDER registration remains that kind on refresh even if a POM later appears; explicit Maven addition at the same path can promote it while retaining identity. A missing POM on a MAVEN registration is not an implicit conversion to FOLDER.

Automatic creation and refresh still discover POMs only. Refresh preserves explicit FOLDER registrations and refreshes their Git metadata without POM parsing or recursively importing their children through the manual-folder action. A missing registered directory stays visible as unavailable, with launch/build operations rejected, until the user removes or repairs it.

**Alternative:** a second folder entity/list avoids nullable Maven fields but duplicates IDs, selections, ordering, import/export, and controller paths. A broad rename of the existing model adds migration risk unrelated to these features.

### 2. Make manual inclusion an explicit explorer mode

Extend `GET /workspaces/{id}/explorer` and `POST /workspaces/{id}/explorer/add` with `includeNonMaven=false`. Add an unchecked Include non-Maven folders control to `fragments/explorer.html`, retaining its value during explorer navigation and passing it for checkbox and direct-path addition. Without opt-in, preserve recursive Maven addition and show an explanatory error for a direct folder with no eligible Maven projects. With opt-in, a selected directory containing a POM keeps existing Maven behavior; a selected directory without its own POM becomes exactly one FOLDER registration. Do not implicitly import every descendant folder. Reopening the dialog resets the opt-in.

In `workspace-detail.html`, show a Folder type label and the basename, retain copy-path, VS Code, explorer, terminal, Git actions where a repository exists, selection, manual ordering, and removal. Hide Maven metadata/properties/version and build controls for FOLDER rows. A shared Maven-capability check also guards direct controller/service calls, full/selected/range builds, POM-specific mutations, and version refresh.

A folder parent must not cause nested Maven build roots to disappear. Derive Maven build roots/hierarchy and topology from MAVEN entries only. Keep folder registrations visible in the shared project list; automatic Maven sorting fills Maven positions while leaving folder positions stable. Drag ordering still applies to all displayed registrations. A mixed build executes eligible Maven entries in original order and reports non-Maven exclusions; a folder-only build starts no process and creates no misleading successful build.

**Alternative:** treating FOLDER as disabled Maven is ambiguous and would allow re-enabling invalid builds. Project kind and enabled/skipped state have separate meanings.

### 3. Extend full workspace text configuration without breaking existing files

Keep existing headers and Maven path lines. Write non-Maven registrations as `NonMavenProject: <path>` in the same ordered sequence. Import recognizes that explicit marker, recalculates only folder/Git data for those entries, and continues parsing legacy plain Maven paths as before. Existing unmarked paths without a POM remain skipped with feedback instead of silently changing old imports into folder discovery. Preserve ordering across kinds and deduplicate normalized paths. Export-only-paths remains exactly one absolute path per registration, without type metadata; full configuration export is the round-trip format.

**Alternative:** infer kind solely from the filesystem at import time. This loses intentional folder registration and changes meaning when a POM is added later. A complete new configuration format is unnecessary.

### 4. Keep Windows terminal launching behind DesktopLauncherService

Use PowerShell 7 (`pwsh.exe`) when available, otherwise Windows PowerShell (`powershell.exe`), opening a separate interactive session that stays open in the validated project directory. Use an OS working-directory argument or a strictly literal/encoded value through a fixed PowerShell launch script; never interpolate a project path as shell instructions. Preserve spaces, apostrophes, Unicode, brackets, and shell metacharacters. Check launcher failure through the existing asynchronous shortcut response and do not fall back to a visible cmd session. Do not change the separate VS Code Windows command just because it currently uses cmd as a launcher.

Keep command selection and launch execution testable through a small process-launch boundary so tests simulate missing executables/errors without opening desktop windows. Add real Windows verification for working directory and window lifetime; Linux unit tests alone cannot prove visible Windows console behavior.

**Alternative:** Windows Terminal is a host and can retain a cmd default profile; selecting it alone does not guarantee a PowerShell session. Requiring PowerShell 7 excludes systems with only Windows PowerShell.

### 5. Resume an immutable server-owned failed build queue

Introduce a bounded in-memory continuation registry alongside build orchestration. A sequential run captures its workspace, ordered eligible Maven IDs, resolved build-profile arguments and Java home. On failure, finish its history as failed and store the failing ID plus the untouched pending tail under an opaque continuation ID. Extend failure notifications with workspace/run/continuation identities; show actions only in the matching workspace. Keep existing notification/history fields compatible.

Add `POST /workspaces/{id}/build-continuations/{continuationId}` with an allowlisted mode (`retry`, `skip`). Retry starts at the failed project. Skip removes exactly that failed project and starts at the next pending eligible project, retaining the original tail order. Do not reconstruct the tail from current global selection or execution order. Both actions use the captured command/Java settings. Revalidate membership, existence, enabled state, Maven kind, and directory availability before scheduling. Reject removed/reclassified or otherwise invalid target queues with a review/restart message; currently disabled projects retain normal skip behavior. Current local source edits do not invalidate an ordinary retry.

Consume a continuation atomically when accepted; disable both controls while submitting, reject duplicate/replayed requests, and restore controls after a definite rejection. Each independent build run has its own identity; UI actions cannot apply another workspace's or run's queue. Cancellation invalidates that run's pending continuation. Expire entries after one hour and retain at most 128 pending entries, evicting oldest entries with an explicit expired response. Restart also requires a fresh build.

When skip has no pending buildable projects, show No remaining projects and start no empty build. Retain the original failed outcome. Otherwise record the resumed execution as a separate build run, show the explicit skipped project in its log/summary/notification, and record timings only for processes actually executed. Add backward-compatible summary/notification metadata for omissions. Successful execution of the remaining tail must not imply that the originally failing project succeeded. Another failure produces a new continuation for its own tail. Do not change persistent enabled flags or silently bypass dependent projects; they may fail normally.

**Alternative:** filter the client-supplied remaining ID array and reuse bulk build. It is smaller but permits cross-workspace/stale/mutated queues and cannot prevent duplicate submissions or distinguish simultaneous failures. The existing bulk endpoint remains for ordinary selected builds with explicit membership validation.

## Risks / Trade-offs

- Maven assumptions spread across entities, templates, hierarchy, properties/version operations, and import/export → audit all consumers and test mixed workspaces, including folder parents containing Maven descendants.
- Schema defaults and old text exports → add an additive/backfilled kind column, preserve legacy Maven semantics, and test reopening an existing H2 database and old configuration files.
- PowerShell quoting/desktop session differences → test argument generation and launch failures, then verify actual Windows paths and interactive windows without weakening shell execution policies.
- Skipping a failed prerequisite can cause later builds to fail → make the omission explicit and retain normal failure handling; never promise dependency correctness or auto-skip dependents.
- Restart/expiry or overlapping runs can invalidate recovery actions → opaque scoped IDs, bounded retention, atomic acceptance, and visible restart guidance.

## Migration Plan

Add/backfill `project_kind` using the application's H2 schema initialization path before enforcing non-null values. Existing registrations remain MAVEN. Extend native-image hints for new enum/continuation/request/notification DTOs. Deploy backend validation with explorer/table/recovery controls together and regenerate bundled CSS after template changes. Persist optional notification metadata compatibly with existing history entries.

For rollback, retain the added column, remove FOLDER registrations from an export-backed workspace before running an older binary that assumes every row has a POM, and keep user directories untouched. Older importers do not understand typed folder lines; use a Maven-only export for them. Continuations are transient and are safely discarded on restart.

## Open Questions

No product decision blocks this proposal. Non-Maven entries are managed folders with Git/local actions, not build adapters. Skipping applies once to a stopped run. Implementation must verify actual Windows launch behavior and document any host limitation before claiming platform verification.
