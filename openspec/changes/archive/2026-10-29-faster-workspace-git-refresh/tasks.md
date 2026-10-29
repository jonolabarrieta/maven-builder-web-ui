# Tasks

## 1. One-process branch label

- [x] 1.1 Change `GitService.getCurrentBranch` to run `git symbolic-ref --quiet HEAD`. Exit 0 builds the label through `ReferenceState(localRef, null, List.of(), null).getLabel()`. Exit 1 falls back to `getCurrentReference(dir).getLabel()`. Anything else returns `n/a`. Verify that the existing `GitReferenceCheckoutTest` cases (branch, detached, outside repository) still pass.
- [x] 1.2 Add `GitReferenceCheckoutTest` cases with real temporary repositories:
  - a branch with commits gives its name
  - a tag-detached HEAD gives `<tag> · detached HEAD`
  - an unborn branch `main` gives `main`
  - a branch name over 255 characters is truncated exactly as before

  Verify that they pass.
- [x] 1.3 Add a test that counts `git` processes for one branch lookup (for example through a `runGit` hook or a debug-log appender) and asserts exactly one on a branch and more than one only when detached. Verify that it passes.

## 2. Rescan without Git

- [x] 2.1 Remove the `getCurrentBranch` calls from `WorkspaceService.scanAndImportProjects`: folder projects, base-path POMs and external-parent POMs. Existing rows must keep their stored branch. Verify with a `WorkspaceHierarchyRefreshTest` case that asserts `gitService` has no interactions during a scan and that a previously stored branch survives.
- [x] 2.2 Verify that import, add-by-path and `WorkspaceGroupService.refresh` still resolve branches: the existing `WorkspaceImportExportTest` and `WorkspaceGroupsIntegrationTest` must pass unchanged.

## 3. Deduplicated, parallel Git phase

- [x] 3.1 Add a shared, fixed-size branch executor to `WorkspaceService` (virtual-thread factory, size from `workspace.git.branch-workers`, default 6, minimum 1), shut down in `@PreDestroy`. Verify with a unit test that the configured size is applied and that 0 is clamped to 1.
- [x] 3.2 Add `refreshGitStatus(Collection<Long> workspaceIds)`:
  - load the top-level projects of every given workspace
  - resolve each project's Git root; a project without one gets `n/a` with no Git call
  - read the distinct roots in parallel through the executor; a failure or timeout gives `n/a` for that root
  - apply the labels and `saveAll` once
  - make `refreshGitStatus(Long)` delegate to it

  Verify with unit tests: five projects sharing one root cause one `getCurrentBranch` call; a project outside any repository causes none; one root throwing leaves the others correct.
- [x] 3.3 Add a concurrency test: with a mocked `getCurrentBranch` that sleeps and tracks how many run at once, 20 roots with a limit of 6 never exceed 6 at once and finish faster than running them sequentially. Verify that it passes.
- [x] 3.4 Change `WorkspaceController.getGitStatusTable` to call `refreshGitStatus` once with all source workspace ids of the composition, then return the composition roots' branches. Verify that the `WorkspaceControllerRefreshTest` cases still pass after updating their mocks, and that a `WorkspaceGroupsIntegrationTest` case shows a repository shared by a consumer and its parent read once.

## 4. Measurement and documentation

- [x] 4.1 Update `WorkspaceScaleBenchmarkTest` to report `scan_git_calls` (expected 0) and a separate Git-phase measurement with `git_calls` and `git_ms` at the default limit. Verify by running it with `-Dworkspace.scale.benchmark=true` and recording the output in the task note.

  Result (161 repositories, 5 ms simulated per read): `scan_git_calls=0 git_calls=161 git_ms=156`. Sequential reads would take about 805 ms. The opt-in benchmark also failed to render before this change, because template variables for groups and blocks were missing; the fixture now supplies them.
- [x] 4.2 Document `workspace.git.branch-workers` in `docs/workspace-git.md`, next to the existing `workspace.git.*` properties, including that `1` restores sequential reads. Verify that the doc renders and names the default.
- [x] 4.3 Run `mvn test`, then do a manual check: refresh a workspace and confirm in the DEBUG log that no `Executing [git` lines appear before the project list is swapped, and that the Git phase logs one `symbolic-ref` per distinct repository.
