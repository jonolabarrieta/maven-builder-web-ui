## 1. Backend Implementation

- [x] 1.1 Add generic `/workspaces/explorer` endpoint in `WorkspaceController.java` to list server/local directory items independent of workspace ID.

## 2. Directory Explorer Fragment

- [x] 2.1 Create `src/main/resources/templates/fragments/directory-explorer.html` to render directory navigation specifically for selecting base paths.

## 3. Workspace Creation Integration

- [x] 3.1 Modify `src/main/resources/templates/index.html` to add a "Browse" button next to the Base Directory Path input in the New Workspace modal.
- [x] 3.2 Add a directory explorer modal structure in `src/main/resources/templates/index.html` and add JavaScript to handle directory selection and updating the input field.

## 4. Workspace Settings Integration

- [x] 4.1 Modify `src/main/resources/templates/workspace-detail.html` to add a "Browse" button next to the Base Directory Path input in the Workspace Settings modal.
- [x] 4.2 Add the directory explorer modal markup in `src/main/resources/templates/workspace-detail.html` and JavaScript to update the input field on directory selection.

## 5. Add Project Modal Path Input Integration

- [x] 5.1 Modify `src/main/resources/templates/fragments/explorer.html` to add a text input field at the top containing the current path, which is fully editable.
- [x] 5.2 Add buttons in the Add Project explorer modal to either load/browse to the typed path, or add the project at the typed path directly.
- [x] 5.3 Implement Javascript helper `addCustomProjectPath()` in `src/main/resources/templates/workspace-detail.html` to send a POST request to add the custom typed/pasted path to the workspace.

## 6. Verification and Testing

- [x] 6.1 Verify creating a new workspace using the file browser and manual path input.
- [x] 6.2 Verify editing workspace settings using the file browser and manual path input.
- [x] 6.3 Verify adding a project using both tree-browsing checkboxes and pasting/typing custom paths in the modal.
