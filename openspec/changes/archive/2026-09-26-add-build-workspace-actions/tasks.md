## 1. Backend: local terminal and exports

- [x] 1.1 Extract existing OS-dependent local launch behavior into a testable launcher service/helper; retain existing Explorer and VS Code behavior.
- [x] 1.2 Implement terminal command selection and asynchronous launching for Windows, macOS, and Linux (`xdg-terminal-exec` plus installed-terminal fallbacks), including actionable launch errors.
- [x] 1.3 Add `POST /projects/{id}/open-terminal`, resolve and validate the module directory, and return an HTMX-compatible success/error response.
- [x] 1.4 Add `GET /workspaces/{id}/export-paths` returning UTF-8 plain text with exactly one absolute project path per line, in execution order, and a safe attachment filename.

## 2. Backend: build ranges

- [x] 2.1 Add a constrained range mode and a `BuildService` method that selects only, from, or through the target project from the workspace's ordered projects.
- [x] 2.2 Add a workspace-scoped range-build endpoint that validates the target module belongs to the workspace and delegates to the existing sequential build execution.
- [x] 2.3 Preserve existing profile selection, Java-home override, disabled-project skips, failure notification, action summary, and retry payload behavior for range builds.

## 3. Frontend

- [x] 3.1 Add **Export Workspace Paths** to `workspace-detail.html`, distinct from Export Order and full Export Workspace.
- [x] 3.2 Add a terminal icon/button next to the existing VS Code and Explorer actions and display launch failures to the user.
- [x] 3.3 Replace/extend each module Build control with an accessible selector for Only this module, Build from here, and Build through here; clear the current log view before a selected range starts.
- [x] 3.4 Retain build-log lines by tab in JavaScript and add a download control that creates a UTF-8 text file containing the current log session.

## 4. Verification

- [x] 4.1 Add unit/controller tests for path-only export content, ordering, UTF-8 content type, and attachment filename.
- [x] 4.2 Add unit/controller tests for project-scoped terminal launch and OS command selection, including unsupported/unavailable Linux terminal reporting.
- [x] 4.3 Add unit/controller tests for inclusive only/from/through ranges, first/last-module boundaries, and rejection of a project from another workspace.
- [ ] 4.4 Run `./mvnw test` (or `mvn test`) and manually verify the four actions in the workspace UI on the available OS; document any OS-specific manual coverage not possible locally.
