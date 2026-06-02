## 1. Parallelize Bulk Git Operations in BuildService

- [x] 1.1 Parallelize `bulkGitFetch` using `CompletableFuture.allOf` to run fetches concurrently.
- [x] 1.2 Parallelize `bulkGitPull` using `CompletableFuture.allOf` and wrap `changed`/`noChanges` in thread-safe synchronized lists.
- [x] 1.3 Parallelize `bulkGitDiscard` using `CompletableFuture.allOf` to run checkouts concurrently.
- [x] 1.4 Parallelize `bulkGitUnstage` using `CompletableFuture.allOf` to run unstaging concurrently.

## 2. Parallelize Bulk Checkout in WorkspaceController

- [x] 2.1 Refactor `bulkCheckout` in `WorkspaceController` to run Git branch creation/checkout concurrently using `CompletableFuture.runAsync`.
- [x] 2.2 Collect checkout results in a thread-safe synchronized list during parallel execution, then update and persist them sequentially using `mavenProjectRepository.saveAll` on the main servlet thread.

## 3. Implement Git Repository Root Resolving & Branch Caching
- [x] 3.1 Implement a helper `findGitRoot(File dir)` in `WorkspaceService` to traverse parent folders to find the Git repository root (by checking for the existence of `.git`).
- [x] 3.2 Refactor `refreshGitStatus` in `WorkspaceService` to group projects by their resolved Git root, query each repository branch once, and perform a bulk database save using `mavenProjectRepository.saveAll`.
- [x] 3.3 Refactor `scanAndImportProjects` in `WorkspaceService` to pass a local `Map<File, String> gitRootBranchCache` through `importProject` helper calls to avoid duplicate system calls.

## 4. Optimize Workspace File Scanner (Skipping Folders)
- [x] 4.1 Update `findPomFiles` in `WorkspaceService` to skip scanning the `src` folder, as well as `build`, `out`, `dist`, `bin`, `gradle`, and `node_modules` folders to avoid unnecessary file I/O operations.

## 5. Verification

- [x] 5.1 Verify that project builds still execute sequentially and respect the correct topological order.
- [x] 5.2 Verify that all bulk Git actions (fetch, pull, checkout, discard, unstage) execute in parallel and succeed.
- [x] 5.3 Verify that loading the workspace detail page performs significantly fewer `git` process calls and is nearly instant.
- [x] 5.4 Verify that scanning/importing a workspace with nested modules skips `src` directories and performs only one Git command per Git root.

