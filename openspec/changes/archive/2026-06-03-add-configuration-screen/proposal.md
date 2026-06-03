## Why

Currently, MvnBuilder uses the default system Java/Maven configuration and does not allow users to customize their working directory shortcuts or build with specific Java versions. Providing a dedicated configuration screen with options for favorite paths and specific JDK versions improves flexibility and control for developers working with multiple Java environments and codebases.

## What Changes

- **New Configuration Screen**: A new dashboard route `/settings` (or a dedicated configuration view) to manage global system options.
- **Favorite Path Configuration**: Ability to define a favorite filesystem path that will be used as the default starting path for the file explorer when creating a workspace or adding projects, as well as prepopulating input fields.
- **Workspace Creation Explorer**: Add a directory browser/explorer button to the "New Workspace" modal that defaults to the favorite path.
- **Java Version Configuration**: Ability to register multiple Java installations (name and `JAVA_HOME` path), select a default active version, and option to select a Java version per workspace.
- **Build Integration**: The build execution engine will run Maven commands using the configured `JAVA_HOME` and update the `PATH` environment variable for the process execution.
- **UI Navigation**: Access to the configuration screen from the top navigation bar of the application.

## Capabilities

### New Capabilities
- `configuration-management`: Defines requirements for configuring and storing global settings (favorite path, registered Java versions) and executing builds using a selected JDK.

### Modified Capabilities
<!-- Leave empty if no requirement changes. -->

## Impact

- **UI/Frontend**: Addition of a new settings configuration page (`templates/settings.html` or fragment), navigation links, and dropdowns for workspace configuration.
- **Backend Controllers**: A new or extended `GlobalSettingsController` to handle settings management (CRUD for Java versions and favorite path).
- **Backend Entities**: New JPA entities `SystemSetting` and `JavaInstallation` to store configurations.
- **Workspace Entity**: Addition of an optional relationship to `JavaInstallation` in `Workspace`.
- **Execution Service**: Update `ProcessExecutionService` to inject `JAVA_HOME` and update the process environment during execution.
