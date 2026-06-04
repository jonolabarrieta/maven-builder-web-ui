## 1. Core Model Changes

- [x] 1.1 Add `hasChildren()` method to `MavenProject.java` to dynamically check if a project has physical subprojects in the workspace.

## 2. Core Service Changes

- [x] 2.1 Refactor parent property loading in `MavenService.java` to extract a recursive helper `getParentPomProperties(File pomFile, Parent parent)`.
- [x] 2.2 Update `resolveProperty` in `MavenService.java` to accept `pomFile` and search parent/grandparent POM properties recursively.
- [x] 2.3 Update `resolveProperty` to resolve standard Maven properties like `${project.parent.version}`, `${parent.version}`, `${project.groupId}`, and `${project.version}`.
- [x] 2.4 Update `parsePom` in `MavenService.java` to pass the POM file parameter to `resolveProperty`.

## 3. Frontend View Changes

- [x] 3.1 Update `workspace-detail.html` to use `project.hasChildren()` instead of `project.modules.empty` for rendering submodule buttons.
- [x] 3.2 Update `project-children.html` to use `child.hasChildren()` instead of `child.modules.empty` for rendering Dive buttons.

## 4. Verification and Testing

- [x] 4.1 Build the application and run unit/integration tests to ensure no regressions.
- [x] 4.2 Scan and verify that module versions are correctly resolved in the web UI.
- [x] 4.3 Verify recursive diving into child submodules works correctly in the modal.
