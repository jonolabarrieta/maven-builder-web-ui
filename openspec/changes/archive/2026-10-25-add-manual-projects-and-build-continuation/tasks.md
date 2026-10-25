## 1. Project kind and persistence

- [x] 1.1 Add ProjectKind MAVEN/FOLDER, shared display/build-capability accessors, explicit parser defaults, and native-image reflection coverage without changing existing project IDs or table identity.
- [x] 1.2 Implement additive H2 kind initialization/backfill for existing registrations and test reopening a database containing pre-change Maven rows.
- [x] 1.3 Add canonical-path deduplication and explicit folder registration in WorkspaceService.addProjectByPath, with existing-directory validation, one-folder semantics, and stable IDs/order on repeated addition.
- [x] 1.4 Preserve folder registrations and their state through scanAndImportProjects, refreshProjectVersions, external-parent scans, and restart; handle unavailable folders and explicit Maven promotion without implicit reclassification.

## 2. Explorer and mixed project management

- [x] 2.1 Extend WorkspaceController.showExplorer and addProjects with default-false includeNonMaven handling, input validation, meaningful addition/error results, and existing Maven-only behavior.
- [x] 2.2 Update fragments/explorer.html with the unchecked opt-in, non-Maven directory selection, navigation-state retention, and matching direct-path/checkbox request payloads; reset the option on a new modal opening.
- [x] 2.3 Separate Maven build hierarchy from registered-folder containment in WorkspaceService and TopologicalSortService; preserve folder positions during auto-sort and retain nested Maven build roots.
- [x] 2.4 Update WorkspaceController.viewWorkspace/addProjectTableModel/getProjectChildren and workspace-detail.html/fragments/project-children.html to render folder identity/type, safe empty metadata, and applicable Git/local controls without Maven build/POM actions.
- [x] 2.5 Enforce Maven capabilities in buildProject, buildAll, buildProjectRange, bulkAction, getProjectProperties, getM2Info, deleteM2Version, and deleteM2Project; validate bulk workspace membership and report mixed/folder-only build exclusions.
- [x] 2.6 Verify updateOrder/calculateOrder/exportOrder and build-profile version refresh honor mixed-workspace semantics; expose Maven execution order without fabricated folder coordinates.
- [x] 2.7 Add service/controller tests for default scans, opt-in additions, duplicates, invalid paths, refresh/promotion/missing folders, direct Maven endpoint rejection, and folder parents with Maven descendants.
- [x] 2.8 Add explorer/table interaction regressions for unchecked defaults, navigation, direct and checkbox additions, refresh selection retention, and mixed ordering/actions.

## 3. Workspace export and import

- [x] 3.1 Extend WorkspaceService.exportWorkspace/importWorkspace with ordered NonMavenProject entries, explicit folder metadata resolution, canonical deduplication, and legacy unmarked Maven semantics.
- [x] 3.2 Verify WorkspaceController.exportWorkspace/exportWorkspacePaths/importWorkspace responses and invalid-entry feedback; retain one absolute path per registration in paths-only export.
- [x] 3.3 Add mixed-kind round-trip, legacy configuration, POM-appearance, unavailable-path, duplicate-path, and order preservation tests.

## 4. Windows PowerShell terminal

- [x] 4.1 Add a testable DesktopLauncherService launch boundary for Windows PowerShell selection, preferring pwsh and falling back to Windows PowerShell without a cmd-session fallback.
- [x] 4.2 Implement a visible persistent PowerShell session in the validated working directory with literal/encoded path handling and observable launch failures; retain Linux/macOS and other shortcut commands.
- [x] 4.3 Verify WorkspaceController.openTerminal/openVsCode/openExplorer and shared directory resolution accept folder registrations, reject unavailable directories, and expose actionable launch responses.
- [x] 4.4 Extend launcher/controller tests for fallback, errors, spaces, apostrophes, Unicode, brackets, and shell metacharacters without launching desktop windows during unit tests.
- [x] 4.5 Verify actual PowerShell windows, lifetime, working directory, and fallback on Windows; record any unavailable host verification explicitly rather than treating command tests as platform verification.

## 5. Server-owned build continuations

- [x] 5.1 Introduce workspace/run-scoped immutable build plans with captured Maven command/Java settings and a bounded continuation registry, one-hour expiry, oldest-entry eviction, and atomic acceptance.
- [x] 5.2 Extend BuildFailure and NotificationService failure publication with workspace/run/continuation identities while preserving existing history/notification compatibility and native DTO hints.
- [x] 5.3 Adapt sequential full/selection/range build orchestration to publish a continuation at first failure, retain failed history, and invalidate pending recovery when its run is canceled.
- [x] 5.4 Implement retry/skip planning from the stored tail, target membership/type/availability validation, normal disabled-project handling, and explicit no-remaining-project outcomes.
- [x] 5.5 Add POST /workspaces/{id}/build-continuations/{continuationId} with allowlisted mode, asynchronous acceptance identity, duplicate rejection, and actionable expired/invalid queue responses.
- [x] 5.6 Add backward-compatible omitted-project metadata to ActionSummary and persisted notifications; record resumed runs separately and log explicitly skipped projects without fabricated successful durations.
- [x] 5.7 Test A/B/C/D retry and skip execution, unchanged enabled flags, captured profile/Java settings, original queue order, folder/disabled exclusions, and no empty process/history run at the tail.
- [x] 5.8 Test cross-workspace IDs, changed/unavailable targets, simultaneous retry/skip, consumed/canceled/expired continuations, bounded retention, and a later failure generating its own continuation.

## 6. Recovery controls and feedback

- [x] 6.1 Update workspace-detail.html failure banner with Skip and continue beside Retry from here, both bound to the scoped continuation endpoint and matching workspace/run events.
- [x] 6.2 Add duplicate-submit/busy state, explicit rejection recovery, no-remaining feedback, and new-run/cancel cleanup while preserving ordinary bulk build selection and logs.
- [x] 6.3 Update workspace-detail.html action summaries/notification cards and any affected build-history rendering for omitted projects and separate resumed-run outcomes.
- [x] 6.4 Add frontend regressions for skip/retry payloads, unrelated-workspace events, simultaneous clicks, failed requests, final-project skip, another failure, and accessible recovery controls.

## 7. Documentation and final verification

- [x] 7.1 Document opt-in folder registration, Maven exclusions, full versus paths-only export, PowerShell behavior, one-run skip semantics, continuation expiry, and dependency-related failure limits; update frontend terminology.
- [x] 7.2 Regenerate bundled CSS for template changes and verify the asset/resource contracts and native-image DTO hints.
- [x] 7.3 Run Maven and relevant explorer, workspace selection/refresh, build/history/notification, launcher, import/export, and Git regressions; confirm the Git view still discovers repositories independently of registration.
- [x] 7.4 Inspect desktop/narrow-screen mixed-project and failure-recovery flows, including default unchecked state and launch-error messages; document platform verification and run strict OpenSpec validation.
