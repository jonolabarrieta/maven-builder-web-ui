## Context

Currently, the application executes bulk Git operations (fetch, pull, checkout, discard, unstage) sequentially across multiple projects in a workspace. These sequential executions block or execute sequentially on threads, which makes switching branches or updating repositories slow for large workspaces. Because Git actions on separate projects are independent, we can run them in parallel.

Additionally, checking Git status and importing projects spawns process builders sequentially for every folder containing a `pom.xml`, causing performance bottlenecks when loading the workspace detail page or scanning/importing. Furthermore, directory scanning walks deep source folders (`src/`) that never contain Maven configuration files.

On the other hand, Maven builds have dependency chains between projects in the workspace. Therefore, the execution order of builds must remain strictly sequential (as calculated by the topological sort) to avoid compiler errors where a project is built before its dependencies.

## Goals / Non-Goals

**Goals:**
- Execute bulk Git operations (Fetch, Pull, Discard, Unstage) concurrently in `BuildService` using asynchronous tasks.
- Execute bulk Git checkout/branch creation concurrently in `WorkspaceController`'s `bulkCheckout` endpoint.
- Respect the existing order for build operations, ensuring sequential execution for builds.
- Ensure thread safety for concurrent log capturing and database updates.
- Minimize Git process creation by detecting the Git repository root of projects and caching branch names.
- Reduce file system walk time by skipping irrelevant directories (like `src/`, `build/`, `out/`, etc.) during POM scanning.
- Batch database operations (`saveAll`) instead of saving in loops.

**Non-Goals:**
- Modifying single-project Git execution logic.
- Executing Maven builds in parallel.

## Decisions

### Decision 1: Parallelize Bulk Git Operations in `BuildService`
We will rewrite the bulk operations (`bulkGitFetch`, `bulkGitPull`, `bulkGitDiscard`, `bulkGitUnstage`) in `BuildService` to run asynchronously in parallel.
- **Approach**: Instead of chaining `CompletableFuture` executions via `thenCompose`, we will trigger them concurrently and collect them in a list of futures. We will use `CompletableFuture.allOf` to wait for all parallel operations to complete before sending the action summary.
- **Alternatives considered**: Chaining the execution sequentially (current behavior, which is slow).

### Decision 2: Parallelize Bulk Branch Checkout with Safe Sequential DB Save
For `bulkCheckout` in `WorkspaceController`, the Git checkouts/branch creations will run in parallel.
- **Approach**: Trigger parallel tasks using `CompletableFuture.runAsync()`. To keep database operations thread-safe and avoid concurrent JPA session updates, checkout results will be added to a thread-safe synchronized list. Once all parallel checkouts complete (`CompletableFuture.allOf().join()`), the main thread will update the entities and perform a single `mavenProjectRepository.saveAll()` call.
- **Alternatives considered**:
  - Running database saves directly inside parallel threads: Rejected due to potential Hibernate session concurrency/locking issues.
  - Sequential checkouts (current behavior): Rejected due to slow response times.

### Decision 3: Use Thread-Safe Lists for Aggregating Results
In parallel executions such as `bulkGitPull`, results must be added to a list concurrently. We will wrap result lists using `Collections.synchronizedList` to ensure they are thread-safe.

### Decision 4: Resolve Git Repository Roots & Cache Branches for Batch Operations
To avoid spawning redundant `git` processes for submodules/projects sharing the same Git repository:
- **Approach**: Walk up the directory structure from the project directory to locate the Git repository root (checking for the presence of a `.git` folder/file). Group/cache Git branches by their Git repository root path.
  - For `refreshGitStatus`: Group projects by their Git root, query the branch once per root, and apply it to all projects in that group.
  - For `scanAndImportProjects`: Pass a local cache map `Map<File, String> gitRootBranchCache` through `importProject` helper calls to cache branch names across POM imports.
  - Save updated projects in batch using `mavenProjectRepository.saveAll(...)` rather than in a loop.

### Decision 5: Exclude Irrelevant Folders from POM File Discovery
To prevent walking deep Java package directories and build folders:
- **Approach**: Modify `findPomFiles` to skip directory names starting with `.` or matching `target`, `node_modules`, `src`, `build`, `out`, `dist`, `bin`, or `gradle`.

## Risks / Trade-offs

- **Risk**: Too many concurrent Git processes running at once could overload system resources (CPU, disk I/O).
  - **Mitigation**: Java's default ForkJoinPool (or Spring's default Executor) will govern the concurrency level, ensuring that it is bounded by the system's available processors.
- **Risk**: Lock contention on Git repositories.
  - **Mitigation**: Each project resides in its own distinct directory, meaning git locks are completely isolated.

