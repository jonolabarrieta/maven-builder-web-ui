## Context

On Windows, execution of synchronous Git commands during the load of the workspace detail page keeps database transactions open for several seconds. H2’s default lock timeout is 1 second, resulting in frequent `Timeout trying to lock table` errors under concurrent traffic or rapid page refreshes.

## Goals / Non-Goals

**Goals:**
- Eliminate the database table lock timeout (`[50200-224]`) on the `MAVEN_PROJECT` table.
- Improve workspace page load speed by returning cached branch names immediately and refreshing them asynchronously.
- Narrow service-layer database transaction scopes by executing Git/POM processes outside `@Transactional` boundaries.

**Non-Goals:**
- Removing the Git branch tracking feature.
- Changing the build runner execution mechanism.

## Decisions

### Decision 1: HTMX Lazy Load for Git Status Refresh
- **Approach**: Remove `workspaceService.refreshGitStatus()` from the initial page GET handler (`WorkspaceController.viewWorkspace`). In `workspace-detail.html`, add `hx-get`, `hx-trigger="load delay:100ms"`, and `hx-swap="outerHTML"` on the `<tbody id="project-table-body">` element to fetch updated branch statuses asynchronously.
- **Alternatives Considered**:
  - *Keep síncrono but optimize*: Still results in slower page loads on large workspaces, though the locking issue would be solved.
- **Rationale**: Provides instant initial page loads (UX improvement) while ensuring the database transaction is short and triggered asynchronously.

### Decision 2: Bypass Global Spinner for Lazy-Load Requests
- **Approach**: Modify `global-spinner.js` to look for a `data-no-spinner` attribute on the HTMX triggering element. If present, it will not trigger the full-screen loading spinner.
- **Rationale**: Prevents the lazy-loaded background request from locking the UI with the full-screen spinner immediately after the page is shown to the user.

### Decision 3: Eager Loading of Excluded Paths
- **Approach**: Change the `Workspace.excludedPaths` `@ElementCollection` to `FetchType.EAGER`.
- **Rationale**: Since database transactions in `WorkspaceService` will be committed immediately to keep lock times low, the loaded `Workspace` entities will become detached. Marking `excludedPaths` as `EAGER` prevents `LazyInitializationException` when these properties are read from detached entities.

### Decision 4: Increase Database Lock Timeout
- **Approach**: Add `;DEFAULT_LOCK_TIMEOUT=10000` to the H2 connection URL.
- **Rationale**: Serves as a defensive fallback to allow database writes up to 10 seconds to resolve lock contention before throwing an error.

## Risks / Trade-offs

- **[Risk]** Stale git branch names are temporarily visible for a split second on page load.
  - *Mitigation*: The UI loads branch names from the DB instantly and updates them as soon as the HTMX request finishes. This is standard modern web application behavior.
- **[Risk]** Large workspaces make many concurrent requests.
  - *Mitigation*: The background refresh is a single batch request per workspace load, not a request per project.
