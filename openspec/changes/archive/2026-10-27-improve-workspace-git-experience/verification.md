# Verification notes

## Automated checks (task 13.2)

- `mvn test`: 186 tests, 0 failures, 1 skipped (the existing opt-in `WorkspaceScaleBenchmarkTest`).
- `python3 src/test/frontend/workspace_git_regression.py`: 149 checks across 20 scenarios, 6 consecutive passing runs. The harness now:
  - fails unless every registered scenario ran;
  - fails on any unexpected error dialog;
  - fails when two scripts export the same name.
- The other frontend regressions (`build_log_tabs`, `git_reference`, `git_stash`, `notification_history`, `workspace_groups`, `workspace_refresh`, `build_recovery`) pass.
- `python3 scripts/build-css.py --check`: `app.css` is current. It was regenerated; Tailwind adds `.contents`, `.!contents` and `.!hidden`, picked up from words in the new scripts. No element uses them.

## Performance measurements (task 13.3)

Both builds ran as real applications (`--no-service`, headless, isolated H2 database and log in a temporary folder). The scenario was driven by `measure.mjs` in the session scratchpad:

- HTTP calls for discovery, status and diff timings.
- A bulk **Fetch** of every repository.
- Headless Chrome over DevTools for the page. Time to a listed sidebar and to all statuses is measured from navigation, after a first discovery. A `MutationObserver` counts DOM updates that touched more than five repository entries ("wide renders").

**Baseline** is the previous commit (`f95be20`); **current** is this change.

### Fixture: 60 repositories, 19,176 directories

Each repository has 2 pending changes, a 150-folder `target/`, a `node_modules` with 200 packages in every third repository, and a local remote whose fetch takes 4 s.

Medians of two alternating runs with default settings:

| Metric | Baseline | Current |
| --- | --- | --- |
| Discovery (directories visited) | 1,392 ms (17,441) | 686 ms (301) |
| Status of all repositories, 4 concurrent | 523 ms | 372 ms |
| Diff, idle | 43 ms | 34 ms |
| Diff during bulk fetch, median / max | 152 / 252 ms | 131 / 225 ms |
| Browser: sidebar listed | 1,653 ms | 343 ms |
| Browser: all statuses shown | 2,149 ms | 627 ms |
| Wide renders while loading | 61 | 1–2 |

With `workspace.git.mutation-workers=12`, fetches occupy the old shared read capacity:

| Metric | Baseline | Current |
| --- | --- | --- |
| Diff during bulk fetch, median / max | 49 / **8,054 ms** | 127 / 167 ms |
| Bulk fetch duration | 33 s | 61 s (`remote-workers`=4) |

The baseline reproduces the reported freeze, with comparisons blocked for several seconds while remote commands hold every read permit. The current build keeps local reads independent. Bulk fetch concurrency is now bounded separately by `workspace.git.remote-workers`, which trades total fetch time for responsiveness and is configurable.

During a bulk fetch, a current-build diff still rises from about 35 ms to about 130 ms. It is not blocked: raising the virtual-thread parallelism did not change it, and the extra time is consistent with contention from the concurrently spawned fetch processes.

### Real repositories: `proyectosPruebas/maven-45`

45 repositories with SSH remotes on GitLab. Before measuring, `random-change.py` added 8 uncommitted changes and 4 local commits. Single run each:

| Metric | Baseline | Current |
| --- | --- | --- |
| Discovery (631 directories in both) | 462 ms | 563 ms |
| Status of all repositories | 337 ms | 194 ms |
| Diff, idle | 37 ms | 24 ms |
| Diff during bulk fetch (network), median / max | 48 / 214 ms | 49 / 203 ms |
| Bulk fetch of 45 remotes | 20.7 s | 19.2 s |
| Browser: sidebar listed / all statuses | 555 / 968 ms | 339 / 561 ms |
| Wide renders while loading | 46 | 1 |

This workspace has no heavy build folders, so discovery does the same work in both builds; the difference is run-to-run noise. Reopening the Git view uses the cached discovery in the current build, so the page lists repositories without a new scan.

## Defects found and fixed during verification

- The scenario script contained a literal closing script tag, which silently truncated the harness, so later scenarios did not run. The harness now asserts that the tag is absent and that every scenario registered.
- Comparison rendering yielded only to animation frames, which never fire in background tabs and headless browsers, so large diffs could stall. It now also resumes on a short timer.
- `editor.js` and `operations.js` both exported `openEditor`, so **Open in VS Code** opened the in-page editor. The VS Code action is now `openInVsCode`, and the harness rejects duplicate exports.
- The choice dialog could be cancelled by a leftover asynchronous `close` event when reopened quickly. Close events are now ignored while the dialog is open.
