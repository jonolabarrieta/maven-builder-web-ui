## 1. Backend Implementation

- [x] 1.1 Add endpoints `/projects/{id}/open-vscode` and `/projects/{id}/open-explorer` to `WorkspaceController`
- [x] 1.2 Implement OS-specific process spawning logic to launch VSCode and File Explorer asynchronously using `ProcessBuilder`

## 2. Frontend Implementation

- [x] 2.1 Add copy-to-clipboard button under project artifact-id showing the relative path in `workspace-detail.html`
- [x] 2.2 Add `copyToClipboard(btn)` JavaScript helper function in `workspace-detail.html` for handling clipboard interaction with visual feedback
- [x] 2.3 Add action buttons in the actions column of `workspace-detail.html` table to trigger the VSCode and File Explorer endpoints using HTMX

## 3. Verification

- [x] 3.1 Verify clicking the project relative path button copies the absolute path to clipboard and shows "Copied!" green feedback
- [x] 3.2 Verify clicking the VSCode button launches Visual Studio Code at the project path
- [x] 3.3 Verify clicking the File Explorer button opens the host's file manager at the project path
