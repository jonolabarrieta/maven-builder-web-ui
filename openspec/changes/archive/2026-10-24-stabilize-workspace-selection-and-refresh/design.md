# Design

## Context

`workspace-detail.html` currently renders cached branches, then its table body starts a background Git request with `hx-swap="outerHTML"`. The returned fragment rebuilds every project row. The page saves checked IDs in `htmx:beforeSwap` and restores them in `htmx:afterSwap`, but a pointer interaction can still coincide with removal of its checkbox. Explicit Refresh is a normal POST and redirect, so the shared navigation spinner can time out after two seconds; the new page then starts a deliberately untracked Git request. Project classification also calls `isParentProject` for each project against the full list, and `hasChildren()` scans a workspace collection while rendering rows.

## Goals / Non-Goals

**Goals**
- Keep project controls stable during automatic branch updates.
- Make selection, Select All, counts, and bulk-action inputs agree after any controlled list refresh.
- Show the complete progress and the final outcome of an explicit Refresh, including the Git phase.
- Reduce repeated hierarchy work and avoid scanning excluded subtrees while preserving results.

**Non-goals**
- Replace Thymeleaf/HTMX with a client-side framework.
- Change Maven builds, bulk Git actions, or persistent workspace data formats.
- Block normal page use while the automatic, on-load Git update runs.

## Decisions

### 1. Update branch indicators without replacing project rows

Keep the cached branch value in the initial page. Move the automatic `hx-get` trigger from `<tbody>` to a stable element outside the table, calling a Git-status endpoint that refreshes the stored branch values and returns one batch response. The response targets only stable branch-indicator IDs, using HTMX out-of-band swaps or an equivalent small client update. It also updates an inline, non-blocking Git status message. A failed request leaves the cached values visible and reports that Git status could not be refreshed.

**Alternative:** morph the whole table with an HTMX extension. Morphing can preserve nodes, but it still sends and processes every row for a branch-only change and requires careful form-state rules. Targeted updates are simpler and cheaper here.

### 2. Make selected IDs the authoritative client state

Maintain a page-scoped `Set` of selected project IDs. Delegated checkbox changes and Select All update that set; the visible checked state, count, and bulk-action payloads derive from it. After an explicit project-list replacement, intersect the set with IDs in the new list, render the checkboxes, and reinitialize sorting once. Automatic Git responses do not touch rows or selection. Selection need not persist across unrelated page navigation or browser restart.

**Alternative:** keep reading checked DOM inputs as the source of truth. That is vulnerable whenever a fragment replaces those inputs.

### 3. Treat explicit Refresh as one two-phase UI operation

Submit the existing Refresh action through HTMX when JavaScript is available; retain the normal POST/redirect response for non-HTMX clients. The HTMX response contains the refreshed table body, which is an intentional structural replacement. Before submission, start a contextual refresh overlay with an accessible phase message and prevent a second Refresh. Mark the HTMX form and its follow-up Git request as owned by that contextual loader so the global spinner does not overlap it. On a successful rescan and swap, reconcile selection, update sorting, and trigger the Git-status request. Keep the overlay until the Git response has been applied or failed; report partial success if the rescan succeeds but Git status fails. On a rescan error or abort, keep the previous table and close the overlay with an error. The overlay's lifetime follows the operation's terminal events, not a two-second navigation timer. The background on-load Git request remains non-blocking; cancel or ignore its stale response if explicit Refresh starts.

**Alternative:** retain full navigation and persist a spinner across page loads. That complicates lifecycle and still leaves the user with a newly rendered table awaiting background Git work.

### 4. Improve large-workspace paths after a baseline measurement

Record timings and counts for filesystem traversal, POM parsing, Git lookup, hierarchy classification, database writes, and HTML/fragment rendering using representative nested workspaces. Replace the repeated all-project parent/child scans used by the workspace table with one normalized-path index or equivalent service-side hierarchy summary. During filesystem traversal, prune configured excluded directories before descending into them. Preserve root `.` semantics, nested modules, external manually added parents, execution order, and enabled state. Keep Git/POM work outside long database transactions. Optimize additional hotspots only if the baseline identifies them.

**Alternative:** paginate the table or migrate its rendering first. The known repeated work and whole-table Git swap should be removed before adding navigation complexity.

## Risks and Verification

- HTMX out-of-band fragments must identify branch elements uniquely and escape branch text safely. Test empty, missing, and failed Git results.
- A rescan may add or remove project IDs. Verify selection intersection, Select All, count, and bulk-action payloads after the swap.
- Slow Git responses can overlap an explicit Refresh. Verify cancellation or stale-response rejection and one terminal status per operation.
- Use a delayed-response browser test for a click at Git completion and a large nested workspace fixture for hierarchy parity. Measure performance before and after without a machine-specific timing threshold.
