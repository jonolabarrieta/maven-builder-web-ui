# Workspace scale baseline

The opt-in `WorkspaceScaleBenchmarkTest` creates 160 sibling Maven projects, a child module under every eighth project, 60 POMs under an excluded subtree, and an external parent with one child. Each sibling is a distinct Git root. A mock Git lookup waits five milliseconds per root so the scan has a repeatable slow-Git component. The test renders the real workspace page after one warm-up render.

With a complete JDK 21 installed, run:

```sh
mvn -q -Dtest=WorkspaceScaleBenchmarkTest \
  -Dworkspace.scale.benchmark=true test
```

This sandbox has only a JDK 25 compiler and a JDK 21 runtime. For verification here, a temporary POM added `<proc>full</proc>` to the Maven compiler plugin and used cached Lombok 1.18.38, then Surefire ran under the JDK 21 runtime with a preloaded Byte Buddy agent. The temporary POM is not part of the change.

Before implementation, one run returned:

| Measure | Baseline |
| --- | ---: |
| Saved projects / top-level projects | 182 / 161 |
| Scan, parse, Git, and save | 977 ms |
| Of scan: simulated Git calls / time | 161 / 839 ms |
| Top-level hierarchy calculation | 6 ms |
| `hasChildren()` pass for displayed parents | 9 ms |
| Warm workspace page render | 157 ms |
| Rendered HTML | 2,387,102 bytes |
| Warm project-table fragment render | 108 ms |
| Rendered fragment | 2,306,114 bytes |

After implementation, the same fixture returned:

| Measure | After |
| --- | ---: |
| Saved projects / top-level projects | 182 / 161 |
| Scan, parse, Git, and save | 1,009 ms |
| Of scan: simulated Git calls / time | 161 / 840 ms |
| Indexed hierarchy calculation | <1 ms |
| Warm workspace page render | 163 ms |
| Rendered HTML | 2,390,384 bytes |
| Explicit Refresh table fragment | 115 ms / 2,303,384 bytes |
| Automatic Git response | 5,381 bytes |

The old `hasChildren()` pass took 13 ms in the second fixture run but is no longer used while rendering rows. The old 2.3 MB table fragment is now sent only when a user explicitly refreshes the project list; automatic Git updates send the 5.4 KB JSON batch. Scan time is still dominated by 161 simulated lookups against distinct Git roots (840 ms), so there is no shared result to cache within this fixture. These are diagnostic measurements, not latency guarantees. The revised UI keeps the explicit Refresh indicator active through both scan and Git phases, including slow distinct-root lookups.

## Verification

The full Java suite passed under the temporary compiler setup: 39 tests, no failures or errors, with the opt-in benchmark skipped during the default suite run. The benchmark passed separately. `src/test/frontend/workspace_refresh_regression.py` ran the current page and global-spinner scripts in headless Chrome with controlled Git and rescan responses. Its 16 checks passed, covering selection while Git is pending, escaped branch text, a rescan lasting longer than two seconds, selection reconciliation, Select All and bulk IDs, one sorting initialization per table body, no duplicate spinner, stale responses, completion, failure, retry, and the normal form fallback when HTMX is unavailable.

The browser harness uses mocked HTTP responses to make timing deterministic. A live manual check against a user's own large workspace was not possible in this isolated test environment. The app still loads HTMX and other browser libraries from CDNs, so the normal POST/redirect fallback remains important when those scripts are unavailable.
