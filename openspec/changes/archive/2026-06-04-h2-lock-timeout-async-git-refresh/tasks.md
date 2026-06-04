## 1. Database & Entity Configuration

- [x] 1.1 Update datasource URL in `application.properties` to append `DEFAULT_LOCK_TIMEOUT=10000`.
- [x] 1.2 Modify `Workspace.java` to change `excludedPaths` `@ElementCollection` fetch type to `FetchType.EAGER`.

## 2. Service Layer Refactoring (Narrow Transactions)

- [x] 2.1 Remove `@Transactional` from `WorkspaceService.refreshGitStatus` and ensure batch save runs at the end.
- [x] 2.2 Refactor `WorkspaceService.scanAndImportProjects` to load existing projects in-memory, parse POMs and Git branches outside transaction, and save in batch. Remove `@Transactional` annotation.
- [x] 2.3 Refactor `WorkspaceService.addProjectByPath` to run outside transaction boundaries and execute a batch save at the end. Remove `@Transactional`.
- [x] 2.4 Refactor `WorkspaceService.importWorkspace` to run outside transaction boundaries, saving Workspace and projects in separate database writes. Remove `@Transactional`.
- [x] 2.5 Remove `@Transactional` from `WorkspaceService.refreshWorkspace`.
- [x] 2.6 Remove the unused private method `WorkspaceService.importProject` from `WorkspaceService.java`.

## 3. Controller & UI Implementation

- [x] 3.1 Update `WorkspaceController.viewWorkspace` to remove the synchronous call to `workspaceService.refreshGitStatus`.
- [x] 3.2 Implement `@GetMapping("/workspaces/{id}/git-status-table")` in `WorkspaceController.java` to refresh Git status and return the projects table body fragment.
- [x] 3.3 Modify `workspace-detail.html` to add `th:fragment="project-table-body"`, `hx-get`, `hx-trigger="load delay:100ms"`, `hx-swap="outerHTML"`, and `data-no-spinner="true"` to `<tbody id="project-table-body">`.
- [x] 3.4 Update `global-spinner.js` to skip displaying the full-screen loading spinner if the triggering HTMX element has a `data-no-spinner` attribute.

## 4. Verification & Testing

- [x] 4.1 Update `WorkspaceImportExportTest.java` to mock and verify the refactored batch save interactions.
- [x] 4.2 Run `mvn clean test` to ensure all tests pass successfully.
