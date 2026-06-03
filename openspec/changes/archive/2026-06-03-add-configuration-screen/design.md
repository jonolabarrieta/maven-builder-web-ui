## Context

MvnBuilder currently runs builds using whatever `mvn` is globally available on the system PATH, with no mechanism to select a specific JDK or configure standard shortcuts like a favorite path. To support teams working with multiple Java versions and clean workspaces, we need a way to manage custom Java installations and default paths.

## Goals / Non-Goals

**Goals:**
- Provide a global Configuration Screen (`/settings/global`) to manage settings.
- Implement storage for favorite filesystem paths and prepopulate workspace creation input with it.
- Allow registration of multiple JDK installations (name and path) and choosing an active one.
- Allow overriding the JDK selection per workspace in workspace settings.
- Inject the active JDK (`JAVA_HOME` and update `PATH`) when spawning Maven build processes.

**Non-Goals:**
- Auto-downloading Java JDKs (users must install JDKs on their system and provide the path).
- Managing Maven settings/installations (we will still use the system `mvn`).

## Decisions

### 1. Database Schema & JPA Entities
We will introduce two new entities:
- `SystemSetting`: Stores general configuration.
  - `id`: Long (fixed to 1)
  - `favoritePath`: String (nullable)
- `JavaInstallation`: Represents a registered JDK.
  - `id`: Long (auto-generated)
  - `name`: String (not null, unique)
  - `javaHome`: String (not null)
  - `isDefault`: boolean
- In `Workspace`: Add a relationship `javaInstallation` (ManyToOne, optional) to allow selecting a workspace-specific JDK.

### 2. JDK Process Execution Injection
We will overload `executeCommand` in `ProcessExecutionService` to accept a `javaHome` parameter:
```java
public CompletableFuture<CommandResult> executeCommand(
    final String label, 
    final File directory, 
    final String javaHome, 
    final String... command
)
```
Inside the implementation, if `javaHome` is present:
- Put `JAVA_HOME = javaHome` in `pb.environment()`.
- Prepend `javaHome + /bin` to the process `PATH` env variable.
The original `executeCommand` without the `javaHome` parameter will call the overload with `null`.

### 3. Global Explorer & Modal Integration
- We will add a global explorer endpoint `/explorer` in `WorkspaceController` that takes a `path` parameter. If `path` is not provided or empty, it will default to the configured `favoritePath` (falling back to the user home directory if not configured).
- This global explorer will render a directory explorer fragment.
- On the `index.html` dashboard, we will add a directory browse button next to the "Base Directory Path" input field inside the "New Workspace" modal.
- Clicking the browse button will load the global file explorer starting at the configured favorite path, allowing the user to navigate the filesystem and select a directory. Selecting the folder will update the workspace path input value.

### 4. UI Navigation & Page Design
- A new Thymeleaf template `settings.html` will be created to render the global configuration screen.
- We will add a link to the "Configuration" page in the navigation bar of `index.html` and `workspace-detail.html`.
- On the configuration page, users can configure the Favorite Path and manage Java installations (Add/Delete/Set Default).
- In the edit workspace modal, we will add a Java Version select dropdown populated with the registered Java installations.

## Risks / Trade-offs

- **[Risk] Path Validity** → If a user inputs an invalid `JAVA_HOME` path, the build will fail immediately.
  *Mitigation*: When executing the command, verify if the provided `javaHome` directory and its `bin/java` (or `bin\java.exe`) executable exist before attempting to launch the process. If not, log a clean error to the log output.
