## 1. Reference identity and current-state resolution

- [x] 1.1 Add service-level reference types and structured current state, separating full Git refs, display names, upstream identity, exact matching tags, and detached commit identity.
- [x] 1.2 Update selector reference listing to supply full refs and configured remote identity while preserving display names and tag version ordering; exclude symbolic remote HEAD refs using Git metadata.
- [x] 1.3 Implement one current-state resolver for attached branches, exact lightweight/annotated tags, and untagged detached commits; define deterministic primary-tag selection and bounded display formatting.
- [x] 1.4 Add temporary-repository tests with distinct commits covering attached branches at tagged commits, annotated/lightweight tags, multiple exact tags, untagged detached commits, unavailable repositories, and long reference labels.

## 2. Type-specific checkout behavior

- [x] 2.1 Implement validated single-project reference checkout with explicit namespaces, local-only legacy resolution, and no Git argument or pathspec guessing; test same-named branches/tags and invalid or missing refs.
- [x] 2.2 Implement creation and activation of local tracking branches from remote choices, preserving nested branch paths and supporting configured remotes other than origin; assert attached HEAD, commit, and upstream in repository tests.
- [x] 2.3 Implement compatible existing-branch reuse and conflict detection for missing/different upstreams; test divergence preservation and verify conflicts leave HEAD, branch tips, and upstreams unchanged.
- [x] 2.4 Implement explicit detached checkout to peeled tag commits; verify that annotated tags and same-named local branches cannot select the wrong target.
- [x] 2.5 Cover Git refusal due to conflicting local edits and verify that no force, reset, discard, fetch, or pull is introduced.

## 3. Controller and cache integration

- [x] 3.1 Update `WorkspaceController.checkoutBranch` to accept `referenceType` alongside `branch`, dispatch validated typed checkout, return actionable validation/conflict failures, and cache freshly resolved actual state only after success; add controller tests including legacy local requests and rejected checkout cache preservation.
- [x] 3.2 Update `WorkspaceController.getBranches` to expose typed choices and fresh current-state/upstream/tag information; verify `fetchAndReloadBranches` delegates to the updated selector model and still excludes symbolic remote HEAD choices.
- [x] 3.3 Route `GitService.getCurrentBranch` through the shared display resolver and audit every `WorkspaceService` scan, import, and Git refresh caller; preserve per-repository caching and the existing `getGitStatusTable` JSON fields. Add service/controller regressions for labels after reload, refresh, rescan, and external checkout.

## 4. Selector and workspace presentation

- [x] 4.1 Update `fragments/branch-selector.html` data attributes and checkout payloads to carry exact refs and types; derive active local, upstream, and exact-tag markers from structured state rather than formatted-string equality.
- [x] 4.2 Update the selector's confirmation messages for local branches, remote tracking destinations, and detached tag checkout; retain cancellation, loading reset, escaped text, and useful Git failure messages.
- [x] 4.3 Integrate reference-oriented labels and any necessary copy changes in `workspace-detail.html`, preserving stable indicator IDs, text-node rendering, selected project state, and stale-response protection.
- [x] 4.4 Add browser regression coverage for Local/Remote/Tags payloads, confirmation cancellation, failed checkout recovery, and tag/tracking-branch labels after reload and background updates; include exact-tag active markers and text escaping.

## 5. Verification

- [x] 5.1 Run the focused Git service, workspace service, controller, and browser regressions plus the existing workspace refresh regression harness; verify that current local selection, sorting, and project selection still pass.
- [x] 5.2 Exercise the complete single-project flow with disposable repositories: create a remote tracking branch, reuse it with local commits, reject an upstream conflict, select both tag kinds, then reload and explicitly refresh. Record actual HEAD, upstream, commit, and displayed-reference results.
