## Context

The Maven Builder Web UI allows users to create workspaces that group Maven projects, and add projects to these workspaces.
- Currently, when creating a workspace, users must manually type/paste the absolute path. No file explorer integration is available.
- When adding projects, users must click through directories starting from the workspace's base path, with no option to directly paste a path they already have.

Integrating a generic directory browser and manual path input fields will improve user experience.

## Goals / Non-Goals

**Goals:**
- Add a file explorer modal to browse and select base directories on the local machine when creating or editing workspaces.
- Retain the text input for base path so users can paste any path they want.
- Add an input text field at the top of the "Add Project" modal so users can paste any path to a project.
- Provide a backend API for listing directory structures without requiring a workspace context.

**Non-Goals:**
- Building a full file manager (e.g., file renaming, deletion, creation).
- Modifying security permissions for accessing directories (we rely on the backend process's read permissions).

## Decisions

### 1. Unified Directory Explorer Fragment
We will create a new HTML fragment template `src/main/resources/templates/fragments/directory-explorer.html`.
- **Why**: Reusing `explorer.html` directly is complex because `explorer.html` is tailored to selecting Maven projects within a workspace, whereas workspace creation needs directory-only selection on the entire local filesystem.
- **Details**: The new fragment will only display subdirectories and a selection action.

### 2. New Backend Endpoint for Generic Browsing
We will add a new endpoint `/workspaces/explorer` to `WorkspaceController.java`:
- **Why**: Existing explorer endpoint `/workspaces/{id}/explorer` requires a workspace ID. We need generic directory browsing when creating a *new* workspace where no workspace ID exists yet.
- **Signature**:
  ```java
  @GetMapping("/workspaces/explorer")
  public String showWorkspaceExplorer(final @RequestParam(required = false) String path, final @RequestParam String targetInputId, final Model model)
  ```
- **targetInputId**: This parameter determines which input field on the page receives the selected path (e.g., `new-workspace-base-path` or `edit-workspace-base-path`).

### 3. Add Project Modal Path Paste Integration
We will modify `src/main/resources/templates/fragments/explorer.html` to add:
- A text input field at the top showing the current path, which is fully editable.
- A "Go" button to reload the explorer view at that path:
  ```html
  <button hx-get="/workspaces/{id}/explorer" hx-include="#custom-path-input" hx-target="#explorer-modal-content">Go</button>
  ```
- An "Add Path" button to add the project at the custom path directly:
  ```html
  <button onclick="addCustomProjectPath()">Add Directly</button>
  ```

## Risks / Trade-offs

- **Security Risk**: Exposing local directory structures to the web frontend.
  - *Mitigation*: The app runs locally as a developer tool (mvnBuilder), so it has the same permissions as the running user. We will restrict directory access to directories readable by the Java process.
- **Styling Consistency**: Maintaining matching Tailwind CSS structures.
  - *Mitigation*: Utilize the same classes and visual language (indigo accents, glassmorphic layout, clean icons) already present in the templates.
