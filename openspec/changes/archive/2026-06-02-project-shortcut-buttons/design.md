## Context

The Maven Builder Web UI allows developers to manage local Maven workspaces. However, there is currently no direct integration with the local developer environment to copy paths or launch tools like VSCode and File Explorer. Since the application runs locally, the Spring Boot backend can interact with the developer's operating system to streamline these workflows.

## Goals / Non-Goals

**Goals:**
- Provide a simple UI component to view relative paths and copy absolute paths.
- Allow opening a project in VSCode directly from the workspace detail page.
- Allow opening a project's directory in the native file explorer across Windows, macOS, and Linux.

**Non-Goals:**
- Support for remote editing/launching (only local systems are supported).
- Integrating other IDEs (IntelliJ, Eclipse, etc.) as part of this specific change.

## Decisions

### 1. Clipboard copying via browser API
- **Approach**: Implement clipboard copying in the frontend using `navigator.clipboard.writeText()`.
- **Rationale**: Browser security models restrict servers from writing directly to the client's clipboard. Doing it in the client via JavaScript is standard. We will provide visual feedback ("Copied!") by temporarily modifying Tailwind classes and text.

### 2. Backend endpoints for local system execution
- **Approach**: Implement POST controller endpoints `/projects/{id}/open-vscode` and `/projects/{id}/open-explorer` in `WorkspaceController.java`.
- **Rationale**: Since the backend runs locally on the host machine, it can spawn local processes. Spawning is asynchronous (returns immediately) so it does not block the HTTP thread pool.

### 3. OS-specific command invocation
- **Approach**: Detect OS using `System.getProperty("os.name")`.
  - **VSCode**:
    - Windows: `cmd.exe /c code <path>` (to properly resolve `code.cmd` or `code.bat` in PATH)
    - macOS & Linux: `code <path>`
  - **File Explorer**:
    - Windows: `explorer.exe <path>`
    - macOS: `open <path>`
    - Linux: `xdg-open <path>`
- **Rationale**: These command strategies are native to their respective platforms and respect user configurations (e.g., default file manager on Linux).

## Risks / Trade-offs

- **[Risk] `code` command is not in the system PATH**
  - **Mitigation**: Catch execution errors in the backend, log them, and return a 500 error response. In the UI, handle HTMX request errors or standard fetch errors and notify the user.
- **[Risk] Spawning processes blocks the main thread or causes zombies**
  - **Mitigation**: Spawning processes via `ProcessBuilder.start()` runs them in a detached state. We will not invoke `process.waitFor()` or wait for stdout/stderr, ensuring the HTTP request completes instantly.
