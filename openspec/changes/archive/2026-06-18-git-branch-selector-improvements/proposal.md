## Why

Developers currently cannot view or check out remote Git branches or tags directly from the workspace UI. Additionally, there is no way to perform a git fetch to update branches dynamically, nor is there a confirmation dialog before checking out references, which could lead to accidental checkouts and loss of context.

## What Changes

- List remote branches as well as local branches in the branch selector modal.
- Add a new "Fetch" button inside the modal to fetch git references and update the modal in real-time.
- Add a dedicated view/tab listing Git tags inside the modal.
- Integrate the confirmation modal (`ConfirmationModal`) to require user approval before performing any branch or tag checkout.

## Capabilities

### New Capabilities
- `git-reference-selector`: Support remote branches, tags, git fetch updates, and checkout confirmation in the branch management UI.

### Modified Capabilities

## Impact

- `net.olaba.mvnbuilder.service.GitService`: Add methods to list remote branches, tags, and perform git fetch synchronously.
- `net.olaba.mvnbuilder.controller.WorkspaceController`: Add endpoints to support fetch-and-reload, remote branches, and tag retrieval.
- `src/main/resources/templates/fragments/branch-selector.html`: Redesign the modal UI with tabs, Fetch button, tag list, and JavaScript confirmation trigger.
