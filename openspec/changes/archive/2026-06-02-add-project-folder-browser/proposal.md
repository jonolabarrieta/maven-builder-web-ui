## Why

Currently, when creating a new workspace or editing its settings, users must manually type or copy-paste the base directory path, which is error-prone and tedious. Conversely, when adding a project to an existing workspace, users are forced to click through a file explorer tree and cannot directly paste an absolute path to a known directory. Adding flexible path browsing and text input options in both scenarios will streamline project and workspace setup.

## What Changes

- **Workspace Creation & Editing**: Add a directory explorer browsing option next to the "Base Directory Path" input field in the "New Workspace" and "Workspace Settings" modals, while keeping the input field fully editable so users can still paste paths directly.
- **Project Addition**: Add a manual text input field at the top of the "Add Project" file explorer modal, enabling users to paste/type an arbitrary absolute path to a project, load it in the explorer, or add it directly, while maintaining the folder tree browser.
- **Backend Directory Listing API**: Introduce a generic backend endpoint (`/workspaces/explorer`) to list directory contents starting from a given path (or user home fallback) without requiring a workspace ID context.

## Capabilities

### New Capabilities
- `project-folder-browse`: Allows users to search the local filesystem via a directory browser or input absolute paths manually when setting up workspaces or adding projects.

### Modified Capabilities
<!-- None -->

## Impact

- `src/main/resources/templates/index.html`: Update workspace creation form to include a "Browse" option.
- `src/main/resources/templates/workspace-detail.html`: Update workspace settings modal to include a "Browse" option. Integrate the path text input and "Browse" triggers.
- `src/main/resources/templates/fragments/explorer.html`: Add an input box at the top of the project explorer modal to paste/edit arbitrary paths and load them.
- `src/main/resources/templates/fragments/directory-explorer.html`: (New fragment) A dedicated directory explorer popup fragment for workspace creation/settings directory selection.
- `src/main/java/net/olaba/mvnbuilder/controller/WorkspaceController.java`: Add a workspace-independent endpoint to list directory contents for the workspace setup browser.
