## 1. Backend Service & Controller Enhancements

- [x] 1.1 Add methods in GitService to retrieve remote branches (`listRemoteBranches`) and list tags (`listTags` sorted version-descending)
- [x] 1.2 Add a method in GitService to execute git fetch synchronously on the project directory
- [x] 1.3 Update WorkspaceController's `getBranches` GET endpoint to load local branches, remote branches, and tags, and add them to the Thymeleaf model
- [x] 1.4 Add a POST endpoint `/projects/{id}/branches/fetch` in WorkspaceController to run git fetch and return the updated branch-selector fragment HTML

## 2. Frontend UI updates (Thymeleaf fragments)

- [x] 2.1 Add tab navigation header (Local, Remote, Tags) in `branch-selector.html`
- [x] 2.2 Add client-side JS `switchBranchTab(tab)` function in `branch-selector.html` to switch between branch lists
- [x] 2.3 Render remote branches list in `branch-selector.html`
- [x] 2.4 Render tags list in `branch-selector.html`
- [x] 2.5 Add a "Fetch" button to the branch selector header that uses HTMX to target `#branch-modal-content` and fetch new references
- [x] 2.6 Integrate `ConfirmationModal.show` when a user clicks on any branch/tag, prompting for confirmation before doing checkout

## 3. Verification

- [x] 3.1 Verify that the branch selector modal displays local branches, remote branches, and tags correctly
- [x] 3.2 Verify that the "Fetch" button successfully retrieves remote updates and refreshes the modal
- [x] 3.3 Verify that clicking a reference prompts with the custom confirmation modal and only triggers checkout on confirmation
