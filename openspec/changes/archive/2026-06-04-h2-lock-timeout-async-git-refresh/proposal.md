## Why

On Windows, the application experiences H2 database lock timeouts (`[50200-224]`) when loading the workspace details page. This is caused by executing slow, synchronous Git status commands inside database transactions, holding table-level locks for several seconds and blocking concurrent requests.

## What Changes

- Modify the workspace detail page to load immediately using cached branch information from the database, without blocking on Git processes.
- Add an asynchronous background request using HTMX to fetch and update the Git branches on page load, updating the UI dynamically once complete.
- Narrow database transaction scopes in the service layer by moving blocking OS processes (like Git calls and Maven pom parsing) completely outside of `@Transactional` boundaries.
- Update the H2 JDBC URL configuration to increase the default lock timeout to 10 seconds.

## Capabilities

### New Capabilities
- `async-git-refresh`: Asynchronously fetch and display the current Git branch names for Maven projects in the workspace detail page.

### Modified Capabilities
- `parallel-git-operations`: Expand git branch status checks to run outside of database transaction boundaries.

## Impact

- `WorkspaceService.java`: Transactional annotations and method signatures for refreshing and importing.
- `WorkspaceController.java`: New endpoint to trigger asynchronous Git status refresh and render the updated table fragment.
- `workspace-detail.html`: HTML structure updated with HTMX lazy loading/triggering.
- `application.properties`: H2 datasource URL update.
