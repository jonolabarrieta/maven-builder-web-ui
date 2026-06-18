## Context

The current Git branch selector in MvnBuilder only shows local branches and lacks features to view remote branches or tags. Additionally, performing a checkout is immediate, with no confirmation dialog, which poses a risk of accidental checkout. Developers also lack a way to pull remote branch listings dynamically without terminal commands.

## Goals / Non-Goals

**Goals:**
- Provide a tabbed modal to select local branches, remote branches, and tags.
- Provide a synchronous fetch action directly in the modal that reloads the modal content.
- Protect checkout operations with a confirmation dialog using the existing `ConfirmationModal` utility.

**Non-Goals:**
- Modifying the underlying database schema.
- Creating a full-fledged git interface (e.g. merge, rebase, cherry-pick) in the UI.

## Decisions

### Client-side switching of reference tabs
- **Option A (HTMX):** Request different views for local branches, remote branches, and tags via separate backend endpoints.
- **Option B (Client-side JS/CSS) [Selected]:** Load all branches, remote branches, and tags in one HTML response and toggle their visibility with simple client-side JavaScript.
- **Rationale:** Option B has zero latency when switching tabs. It feels instantaneous and premium.

### Synchronous Git Fetch for modal updates
- **Option A (Asynchronous):** Run fetch in the background and use WebSocket logs to indicate progress.
- **Option B (Synchronous GET/POST swap) [Selected]:** Trigger fetch synchronously when requested, block the UI briefly with a loader, and reload the modal HTML fragment once completed.
- **Rationale:** Git fetch is typically very fast (1-2 seconds). Synchronous execution allows for a simpler implementation and direct modal refresh.

### Checkout behavior
- **Decision:** Execute `git checkout <ref>` directly in the backend. When checking out a tag or a remote branch, Git naturally enters a detached HEAD state or checks out the reference. The UI will reload the page to display the current reference name.

## Risks / Trade-offs

- **Risk:** Slow network connection might make git fetch slow.
- **Mitigation:** The modal will display a loading spinner when fetch is triggered, and a timeout of 15 seconds will be enforced on git commands.

- **Risk:** Detached HEAD state confuses the user.
- **Mitigation:** The confirmation modal warns the user about changing to that reference point.
