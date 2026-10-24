# Tasks

## 1. Baseline and Backend Data

- [x] 1.1 Build a representative nested-workspace fixture with many modules, an excluded subtree, an external parent, and a controllable slow-Git case.
- [x] 1.2 Record baseline rescan, hierarchy, Git, and view/fragment timings with that fixture; keep the measurement method reproducible without an absolute timing threshold.
- [x] 1.3 Replace repeated top-level classification in `WorkspaceService` with one normalized-path hierarchy summary. Preserve root, external-parent, order, and enabled semantics.
- [x] 1.4 Update `WorkspaceController.viewWorkspace` and the table portion of `workspace-detail.html` to use precomputed child indicators instead of calling `hasChildren()` for each row.
- [x] 1.5 Change `WorkspaceService` POM traversal to prune configured excluded directories before descending, while retaining the current rules for manually added external parents.
- [x] 1.6 Add service tests comparing project membership and hierarchy before/after refresh for nested modules, excluded paths, root POM, and external parents.

## 2. Targeted Git Refresh

- [x] 2.1 Change the Git-status method in `WorkspaceController` to return one batch of escaped, project-ID-targeted branch updates and a contextual completion state, rather than a project-table-body fragment.
- [x] 2.2 Update `workspace-detail.html` so the automatic Git trigger and status live outside `<tbody>` and every branch indicator has a stable project ID. Remove automatic `outerHTML` replacement of the table body.
- [x] 2.3 Add controller/fragment tests for branch-only response content, unknown or failed Git status, and the absence of replacement project rows or checkboxes.

## 3. Selection State

- [x] 3.1 Refactor the selection handlers in `workspace-detail.html` to maintain a page-scoped set of project IDs; derive checkbox state, count, Select All, and bulk-action payloads from that set.
- [x] 3.2 Reconcile that set after an intentional table-body replacement and reinitialize row sorting exactly once. Remove the old beforeSwap/afterSwap snapshot variable.
- [x] 3.3 Add a frontend interaction regression for selection during a delayed Git response and for added/removed projects after explicit Refresh, including the resulting bulk-action IDs.

## 4. Explicit Refresh Lifecycle

- [x] 4.1 Update `WorkspaceController.refreshWorkspace` to return the refreshed table-body fragment for an HTMX request and preserve the POST/redirect fallback for a normal form submission.
- [x] 4.2 Update the Refresh form and add an accessible contextual phase indicator in `workspace-detail.html`; make the contextual loader own the HTMX requests so it does not duplicate the global overlay.
- [x] 4.3 Implement the client refresh coordinator for the rescan, controlled list swap, selection reconciliation, and Git update phases.
- [x] 4.4 Add completion/error/abort cleanup, duplicate-click prevention, and stale on-load Git response rejection to that coordinator.
- [x] 4.5 Add controller tests for HTMX and normal-form responses.
- [x] 4.6 Add frontend lifecycle regressions for a rescan longer than two seconds, rescan failure, Git failure after rescan, and overlapping Git requests.

## 5. Final Verification

- [x] 5.1 Re-run the baseline fixture and compare measured work with the original path. Investigate any remaining dominant bottleneck before deciding on further optimization.
- [x] 5.2 Run the project tests and manually verify click/selection behavior with a delayed Git response, refresh phases on a large workspace, Select All, sorting, and retry after failure. Record any environment-limited checks.
