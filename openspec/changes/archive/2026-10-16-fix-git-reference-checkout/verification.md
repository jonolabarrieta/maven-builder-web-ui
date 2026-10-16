# Verification

Validated on 2026-10-16 with Java 21 and local Git repositories created under temporary test directories.

## Automated checks

- Full Maven test suite: **57 discovered, 56 passed, 1 skipped, 0 failures/errors**. The skipped test is the existing opt-in `WorkspaceScaleBenchmarkTest`.
- New real-repository tests: **13 passed** in `GitReferenceCheckoutTest`.
- New HTTP/Thymeleaf/workspace integration tests: **5 passed** in `WorkspaceGitReferenceTest`.
- Chrome reference-selector harness: all checks passed for tracking-branch, detached-tag, and escaped-reference fixtures rendered by the integration tests.
- Existing Chrome workspace-refresh harness: all checks passed, including the new tag/tracking-reference update check and existing selection, stale-response, bulk-action, sorting, and failure-recovery checks.
- `git diff --check`: passed.
- `openspec validate fix-git-reference-checkout --strict --no-interactive`: passed.

The Maven invocation preloaded the installed Byte Buddy agent through Surefire's `argLine` because this sandbox prevents Mockito from attaching an agent dynamically. Chrome harnesses ran outside the process sandbox against temporary local HTML files. No runtime dependency or project test configuration was added for those environment constraints.

## Repository and HTTP results

| Flow | Observed HEAD/upstream/commit and display assertions |
| --- | --- |
| First selection of `origin/feature/demo` | Symbolic HEAD is `refs/heads/feature/demo`; upstream is `refs/remotes/origin/feature/demo`; HEAD equals the remote commit captured before checkout; cached and refreshed label is `feature/demo`. |
| Reuse after a local commit | HEAD equals the recorded extra local commit, including when remote history diverges; upstream is preserved; reload, Git-status request, and explicit workspace rescan retain `feature/demo`. |
| Existing candidate without/mismatched upstream | Conflict is reported before switching; HEAD remains on `main`; local tip and upstream configuration remain unchanged; HTTP returns 409 and does not persist the requested ref. |
| Lightweight `v1.0.0` | HEAD is detached and equals the exact tag commit captured before checkout; label remains meaningful after status refresh and rescan. |
| Annotated `v2.0.0` | HEAD is detached at the peeled tag commit; both exact tag aliases are active in the selector; the deterministic primary label is `v2.0.0 · detached HEAD`. |
| External switch back to `main` | Git-status response and subsequently rendered selector identify `main`, replacing the detached tag state. |
| Dirty working tree | Git rejects checkout; original file contents, symbolic HEAD, and cached label remain unchanged. |
| Same-named local branch and tag | Typed Local activates the branch; typed Tags detaches at the tag's distinct commit. |

Browser checks use the production selector and confirmation code with controlled transport and a navigation counter. Actual Git effects, HTTP contracts, rendered active markers, reload lookups, and rescans are verified in the integration tests. Native reflection registrations were added for the new template models; a native executable was not built as part of this verification.
