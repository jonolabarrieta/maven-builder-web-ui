## Why

Currently, Maven submodules are not recursively displayed beyond the first level in the workspace view. In addition, module versions defined via custom properties (e.g., `${revision}`) inherited from parent POM files are not resolved correctly in the web UI, displaying raw property strings instead of actual versions. This proposal addresses these issues to ensure accurate hierarchical module visualization and correct version presentation.

## What Changes

- Add a dynamic verification (`hasChildren()`) to determine if a Maven project contains any physical submodules within the workspace database instead of relying on the raw XML `<modules>` list.
- Replace the `.modules.empty` checks in the frontend views (the main project table and the child modal) with the new dynamic check.
- Update `MavenService` to recursively resolve property placeholders in POM elements (like version and group ID) using parent POM properties, resolving placeholders such as `${revision}`.
- Resolve standard Maven properties like `${project.parent.version}`, `${parent.version}`, `${project.groupId}`, and `${project.version}` automatically during parsing.

## Capabilities

### New Capabilities
- `recursive-modules-display`: Recursive retrieval and display of child Maven modules in the UI, and robust resolution of their versions using parent POM property inheritance.

### Modified Capabilities
<!-- Leave empty as there are no existing spec files -->

## Impact

- **Affected Code**: `MavenProject.java`, `MavenService.java`, `WorkspaceController.java`, `workspace-detail.html`, `project-children.html`
- **Dependencies**: No external dependency changes.
- **Database**: H2 schema remains unchanged.
