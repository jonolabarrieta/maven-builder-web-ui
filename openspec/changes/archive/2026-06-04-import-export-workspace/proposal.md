## Why

Users currently have no way to migrate, backup, or share workspaces between different environments or instances. Providing a plain text import and export mechanism makes workspaces portable, simple to edit manually, and easy to restore without storing redundant database metadata.

## What Changes

- Add an "Export Workspace" button on the workspace detail page to download a plain-text file containing the workspace name, base path, excluded paths, and project paths in their exact execution order.
- Add an "Import Workspace" button/modal on the homepage allowing users to upload a plain-text workspace export file or paste the text configuration directly.
- Implement importing logic that parses the plain-text configuration, creates the workspace, finds and parses the projects' POM files from disk to recalculate all metadata (such as dependencies, branches, version, modules), and preserves the execution order of the imported projects.

## Capabilities

### New Capabilities
- `workspace-import-export`: Ability to export and import a workspace's configuration as plain text containing project paths, maintaining their order, and recalculating metadata upon import.

### Modified Capabilities
<!-- Leave empty as no existing requirements are changing -->

## Impact

- **UI / Thymeleaf Templates**: `index.html` (add import workspace action/modal) and `workspace-detail.html` (add export workspace button).
- **Backend Controllers**: `WorkspaceController.java` (add import/export endpoints).
- **Backend Services**: `WorkspaceService.java` (implement plain-text serialization/parsing logic, creating workspace from plain text and ordering imported projects).
