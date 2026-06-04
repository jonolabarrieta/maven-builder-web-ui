## Why

Currently, developers using the Maven Builder Web UI have to manually copy paths or navigate in their terminal/file manager/IDE to open project directories or codebases, which slows down development loop efficiency. Providing quick integration buttons directly in the web UI will improve developer productivity significantly.

## What Changes

- Add a copyable button/badge in the project column displaying the project's relative path; clicking it copies the absolute path to the user's clipboard.
- Add a quick action button in the project list to open the project in Visual Studio Code (VSCode) using the OS-agnostic command line interface.
- Add a quick action button in the project list to open the project in the system's native file explorer, supporting Windows, macOS, and Linux.
- Add new HTTP endpoints in the backend to handle the VSCode and File Explorer open requests securely and asynchronously.

## Capabilities

### New Capabilities
- `project-local-shortcuts`: Local shortcuts per project for copying the absolute path, launching VSCode, and opening the native file explorer at the project directory.

### Modified Capabilities
<!-- No modified capabilities since there are no existing specifications -->

## Impact

- `src/main/resources/templates/workspace-detail.html`: HTML modifications to add path copy badge, action buttons, and copy JS function.
- `src/main/java/net/olaba/mvnbuilder/controller/WorkspaceController.java`: New controller methods mapped to open VSCode and open file explorer endpoints.
- `src/main/java/net/olaba/mvnbuilder/service/FileSystemService.java` or `WorkspaceController.java`: OS-specific system execution commands using `ProcessBuilder`.
