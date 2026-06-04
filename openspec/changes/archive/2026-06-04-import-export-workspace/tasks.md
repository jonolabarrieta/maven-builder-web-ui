## 1. Core Service Implementation

- [x] 1.1 Implement serialization logic in `WorkspaceService.java` to export a workspace as a plain text string
- [x] 1.2 Implement parsing and importing logic in `WorkspaceService.java` to create a workspace and import projects from plain text
- [x] 1.3 Ensure imported projects have execution order preserved and metadata dynamically recalculated

## 2. Controller Integration

- [x] 2.1 Add GET `/workspaces/{id}/export` endpoint in `WorkspaceController.java` to download the plain text representation of a workspace
- [x] 2.2 Add POST `/workspaces/import` endpoint in `WorkspaceController.java` to handle plain text upload or form input for workspace importing

## 3. UI / Views Integration

- [x] 3.1 Update `workspace-detail.html` to include a prominent "Export Workspace" action button
- [x] 3.2 Update `index.html` to add an "Import Workspace" action button next to "+ New Workspace"
- [x] 3.3 Create the "Import Workspace" modal in `index.html` supporting file upload

## 4. Verification

- [x] 4.1 Perform verification by exporting an existing workspace, importing it back, and asserting name, paths, and execution order are preserved
