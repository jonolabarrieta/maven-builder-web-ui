## Why

Currently, bulk Git operations (fetch, pull, checkout, discard, unstage) across multiple projects in a workspace are executed sequentially. This is slow and blocks the application thread or queues operations one after another, leading to sub-optimal performance. Since Git operations across different projects are completely independent, they can and should run in parallel to dramatically improve performance and speed up developers' workflows.

Additionally, when loading the workspace detail page, `refreshGitStatus` runs `git rev-parse` sequentially for all top-level projects, causing long load times. Similarly, scanning for projects runs `git` for every submodule/pom file found, and recursively walks deep source folders (`src/`), causing high CPU and disk I/O usage. Resolving Git branch names per Git repository root and skipping unnecessary directories during file scanning will drastically improve loading and import performance.

## What Changes

- Modify bulk Git operations (`bulkGitFetch`, `bulkGitPull`, `bulkGitDiscard`, `bulkGitUnstage` in `BuildService`) to run Git commands concurrently using `CompletableFuture.allOf` or executing them in parallel streams.
- Modify the bulk checkout endpoint (`bulkCheckout` in `WorkspaceController`) to run checkout operations in parallel instead of sequentially.
- Optimize `refreshGitStatus` and `scanAndImportProjects` in `WorkspaceService` to group projects by their Git repository root, caching branch names to avoid duplicate git process spawns.
- Optimize directory scanning in `findPomFiles` (`WorkspaceService`) to skip directories that never contain POM files, such as `src` and build artifact folders (`build`, `out`, etc.).
- Ensure that workspace build operations continue to respect the calculated topological execution order, while Git operations execute in parallel without ordering constraints.

## Capabilities

### New Capabilities

- `parallel-git-operations`: Implement parallel execution of Git commands (fetch, pull, checkout, discard, restore/unstage) across selected workspace projects.
- `workspace-performance-optimization`: Implement Git root caching and directory scan exclusions for instant page loads and scans.

### Modified Capabilities

<!-- None -->

## Impact

- `BuildService.java`: Bulk Git operations will be refactored to trigger concurrent process executions.
- `WorkspaceController.java`: The bulk checkout endpoint will run processes in parallel rather than blocking sequentially in a loop.
- `WorkspaceService.java`:
  - `refreshGitStatus` and `scanAndImportProjects` will be updated to resolve Git repository roots and cache branches.
  - `findPomFiles` will skip `src`, `build`, `out`, `dist`, `bin`, `gradle`, and `node_modules` folders.
  - Batch saves (`saveAll`) will be used to reduce database access overhead.
- `ProcessExecutionService.java`: Already supports concurrent execution through `activeProcesses` (using thread-safe structures and WebSocket status broadcasts), but we will verify its thread safety under parallel Git loads.

