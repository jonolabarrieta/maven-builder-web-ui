## Context

The user confirmed that Local works and the confusing `HEAD` state occurs only with Remote and Tags. `GitService.checkoutBranch` currently runs the same `git checkout <name>` for all tabs. The controller stores the submitted name as `MavenProject.gitBranch`; subsequent scan and Git-status paths call `getCurrentBranch`, which runs `git rev-parse --abbrev-ref HEAD` and replaces that name with `HEAD` for detached checkouts.

A temporary repository reproduction with different commits confirmed that tags and remote references reach their requested commits. The defects are remote branch activation semantics and current-reference identification, rather than evidence that Git checked out an unrelated commit.

The workspace already has targeted JSON branch-indicator updates from `stabilize-workspace-selection-and-refresh`. Its artifacts are complete but not yet archived, so the baseline `async-git-refresh` spec still describes older table swaps. This change builds on the current implementation and leaves that transport and lifecycle intact. Existing unrelated frontend edits must be preserved during implementation.

## Goals / Non-Goals

**Goals:**
- Carry exact reference identity and type through single-project selection and checkout.
- Leave remote selections on a local branch tracking the selected remote branch.
- Make tag checkout explicitly detached and display meaningful tag or commit information after reload, background refresh, and workspace scans.
- Keep local branch selection, Git failures, confirmation cancellation, and project selection reliable.

**Non-Goals:**
- Automatically fetch, pull, merge, rebase, reset, discard changes, or force a checkout.
- Redesign the selector, implement general Git history browsing, or change bulk checkout semantics.
- Introduce a runtime library or require a database migration.

## Decisions

### 1. Separate reference identity from presentation

Introduce a small Git reference type and state representation inside the service boundary. Selector choices contain a type (`LOCAL`, `REMOTE`, `TAG`) and an exact full ref in the matching namespace (`refs/heads/`, `refs/remotes/`, `refs/tags/`). Display labels remain ordinary branch, remote, or tag names. Obtain remote metadata from full refs and configured remotes, preserving nested branch names and excluding symbolic remote HEAD refs by their symbolic target instead of looking for `->` in display text.

Keep `/projects/{id}/checkout` and the existing `branch` form field, adding `referenceType`. The updated selector sends both type and full ref. A request without a type remains a legacy local-branch request; it must resolve only an existing local branch. Unknown types, missing refs, and namespace mismatches fail before executing a mutating Git command. Pass arguments directly to `ProcessBuilder` and use explicit ref validation rather than interpreting a submitted value as a Git option or pathspec.

**Alternative:** infer the type from names such as `origin/feature` or try Git's checkout guessing. Rejected because a local branch, remote ref, and tag can share similar names, and guessing is the source of the current behavior.

### 2. Remote selection activates a local tracking branch

Derive the candidate local name from the remote branch path after removing the configured remote name: `origin/feature/demo` becomes `feature/demo`.

- If the local branch does not exist, create and activate it at the selected remote ref, with that ref configured as its upstream.
- If it exists and its upstream is exactly the selected remote branch, activate it without moving its tip. Local commits or divergence remain intact; selecting a remote branch is not a pull operation.
- If that local name exists without an upstream or with another upstream, return an actionable conflict before switching. Do not overwrite it, reset it, or silently alter its upstream.

Use explicit branch-switching commands supported by the project's existing Git baseline; do not require a newer Git version solely for `git switch`. Normal Git protection of uncommitted files remains in force.

**Alternative:** continue detached checkout for Remote. Rejected because the requested behavior is to work on the branch. Automatically resetting an existing local branch to the remote tip is also rejected because it changes local history unnecessarily.

### 3. Tags explicitly detach; Git determines the current state

Resolve the tag's full ref to its peeled commit and perform an explicit detached checkout. This supports lightweight and annotated tags and cannot accidentally activate a same-named local branch.

Use one current-state resolver for checkout results, selector state, and all existing scan/refresh call sites:

1. An attached symbolic HEAD produces the actual local branch name, even if that commit also has tags.
2. A detached HEAD with exact tags produces tag information and an explicit detached marker, for example `v1.0.0 · detached HEAD`.
3. A detached HEAD without an exact tag produces an abbreviated commit and detached marker, for example `a1b2c3d · detached HEAD`.
4. A failed Git lookup preserves the existing unavailable/error behavior and does not invent a successful checkout.

Use only exact commit matches for tags, peeling annotated tags; do not use a nearest-ancestor tag as the current tag. If multiple tags identify the detached commit, choose a deterministic primary tag using the existing descending version ordering and expose the complete matching set in the selector. Highlight every exact matching tag there. Git does not store which alias the user clicked, so no new persistent selection history is introduced.

**Alternative:** display the submitted name indefinitely. Rejected because an external checkout, a compatible local branch with extra commits, or a subsequent scan could make that cached name incorrect. Bare `HEAD` and inferred nearest tags are also insufficient identifiers.

### 4. Keep display caching compatible and use typed state for selection

Retain `MavenProject.gitBranch` as the cached display string and the existing Git-status JSON shape (`projectId`, `branch`). Route `getCurrentBranch` and its scan, import, and refresh callers through the same resolver/formatter so an update cannot overwrite a tag label with bare `HEAD`. Store a freshly resolved label after successful checkout instead of the submitted ref.

`getBranches` obtains fresh structured state to mark the active local branch and exact current tags. It must not compare a formatted detached label with a raw tag name. A remote row can indicate its active tracking branch through upstream identity, rather than by comparing `origin/name` with a local branch label.

The workspace continues applying returned labels through text nodes and stable project IDs. Maintain its background-request cancellation, explicit-refresh lifecycle, and selection preservation. Changes to the workspace template are needed only for reference-oriented copy or display integration; represent any such edits in the implementation tasks.

**Alternative:** add persistent reference-type and selected-tag columns. Rejected for this fix because current Git state can be resolved on demand, existing cached labels remain compatible, and retaining a clicked alias is not required to identify the checked-out commit.

### 5. Explain the destination before checkout and preserve recoverable failures

Keep `ConfirmationModal` and the current reload-after-success behavior. Local confirmation names the local branch. Remote confirmation explains the local branch and upstream destination. Tag confirmation names the tag and explicitly explains detached HEAD. Cancellation sends no checkout request.

Validation and upstream conflicts return useful client errors; Git checkout failures retain the Git message. The selector resets loading on failure and leaves the current cached reference unchanged when Git rejected the operation. No force or automatic cleanup is added.

## Risks / Trade-offs

- **Existing local branch has no upstream:** Remote selection reports a conflict even if its name looks compatible. This avoids guessing; the user can select that branch in Local or configure its upstream explicitly.
- **Existing tracking branch has diverged:** Activation preserves its current commit. Confirmation explains that the operation switches branches without synchronizing history.
- **Several tags point to one commit:** The primary displayed tag is deterministic rather than a remembered click. The selector exposes all exact aliases and tests verify that they resolve to the same commit.
- **Additional Git queries on large workspaces:** Keep the current per-repository cache during scans/refreshes and perform exact-tag queries only for detached HEAD.
- **Long tag names and the cached database column:** Bound the formatted label to the existing storage limit without losing its detached marker; retain full names in selector data and accessible text. Test long labels and escaped text.
- **A checkout fails because of local modifications:** Preserve Git's refusal and show its message; do not force checkout or change the cached label to the requested destination.

## Migration Plan

No schema migration or cache backfill is required. Existing plain local labels remain valid. Existing cached `HEAD` labels become meaningful after the normal background lookup or an explicit refresh. Ship the form, controller, and resolver updates together. A rollback can read the same string cache, although it restores the old detached-label limitation; Git branches already created by successful checkout remain ordinary local branches.

## Open Questions

None blocking. The confirmed scope is the single-project Remote and Tags selector, with Local retained as regression coverage.
