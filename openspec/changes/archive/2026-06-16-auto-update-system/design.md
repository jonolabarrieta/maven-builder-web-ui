## Context

The MvnBuilder application is built with Spring Boot and can run as an executable JAR or a GraalVM native binary. It runs on Windows, Linux, and macOS. To implement an in-app self-updater, we must check for updates, download the appropriate asset, overwrite the running executable/JAR, and restart the process.

## Goals / Non-Goals

**Goals:**
- Add an "Update Center" component to the Configuration (`settings.html`) page.
- Allow manual update checking via a dedicated button (no automatic checking).
- Support updating both packaged JARs and GraalVM native binaries.
- Ensure the update checker queries a configurable URL that can be mocked locally.
- Provide a clear, robust mechanism for updating the application on Windows, Linux, and macOS without causing file-lock errors.
- Document how developers can test the updater flow locally using a mock server.

**Non-Goals:**
- Automatic background update checks.
- Automatic installation of updates without user confirmation.
- Rollback mechanisms if the new version fails (handled by manual re-installation).

## Decisions

### 1. Update Check Protocol and Payload
To allow easy mocking, we will use a simple JSON format. The client will query a configurable URL via `java.net.http.HttpClient` (with redirects enabled).
- **GitHub Release Format (Default)**: The standard GitHub release payload.
- **Mock/Custom Format**: The updater will accept a simple JSON payload containing:
  ```json
  {
    "tag_name": "v1.2.0",
    "html_url": "https://github.com/.../releases/tag/v1.2.0",
    "body": "Release notes here...",
    "assets": [
      {
        "name": "mvn-builder.jar",
        "browser_download_url": "http://localhost:8080/downloads/mvn-builder.jar"
      },
      {
        "name": "mvn-builder-linux",
        "browser_download_url": "http://localhost:8080/downloads/mvn-builder-linux"
      }
    ]
  }
  ```
- *Rationale*: By matching the basic structure of the GitHub Releases API, we don't need separate parsers for mock testing and production. The check URL property in `application.properties` can simply be pointed to a local mock server.

### 2. Detection of Running Mode (JAR vs Native Binary)
- *Decision*: We will dynamically inspect the code source URL of the main application class.
  - If the path ends with `.jar`, we are in **JAR mode**. We replace the JAR file and restart using `java -jar <jarPath>`.
  - Otherwise, we are in **Native Binary mode**. We retrieve the executable path using `ProcessHandle.current().info().command()` and restart by executing the binary directly.
- *Alternative*: Hardcoding the mode during compilation or using profiles.
- *Rationale*: Dynamic runtime detection avoids build-time profile complexity and reduces bugs.

### 3. File Lock Handling (Process Replacement)
- *Decision*: Write a platform-specific updater script (`update.bat` for Windows, `update.sh` for Unix/macOS) to a temporary directory.
  - The script waits for the parent process ID (PID) to exit, overwrites the target file with the downloaded temporary file, launches the new application process, and deletes itself.
  - The application spawns this script using `ProcessBuilder` (detached) and calls `System.exit(0)`.
- *Rationale*: On Windows, running executables and JARs are locked by the OS. Spawning a detached script that waits for the parent process to die is the only reliable cross-platform solution.

## Risks / Trade-offs

- **[Risk]** The updater script fails to launch or replace the file, leaving the app closed and not restarted.
  - *Mitigation*: Write detailed logs to the update script (e.g., `update.log` in the temp directory). Advise the user in the UI that the app is restarting.
- **[Risk]** Security risk from downloading arbitrary binaries.
  - *Mitigation*: The download URL is configured on the server-side, not provided by the client. We only download assets from the configured update URL.
- **[Risk]** Platform compatibility of scripts.
  - *Mitigation*: Keep scripts extremely simple and avoid complex shell commands. Use basic commands (`move`, `del`, `mv`, `rm`) standard in command-line environments.
