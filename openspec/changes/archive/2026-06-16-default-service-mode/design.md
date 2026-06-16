## Context

Currently, `MvnBuilderApplication` runs in foreground mode by default, unless the `--service` command-line argument is passed. If `--service` is supplied, it installs OS-specific autostart settings and spawns a background process, exiting the foreground CLI. Under the new requirement, the application should automatically launch as a background service by default when executed from a packaged JAR, and require a `--no-service` argument to run in the foreground attached to the terminal.

## Goals / Non-Goals

**Goals:**
- Default to background service registration and execution when running from a packaged JAR without arguments.
- Support `--no-service` (and variations like `no-service`, `-no-service`) to run the application in the foreground.
- Update autostart configuration generators for Linux, Windows, and macOS to append `--no-service` to the startup commands to prevent infinite execution loops.
- Prevent application startup exit when executing in development/IDE mode (non-JAR).

**Non-Goals:**
- Modifying directory structures, system service templates, or changing the underlying registry/daemon setup logic.

## Decisions

### 1. Safe JAR Detection in `main()`
To prevent the application from exiting immediately in development/IDE environments, we must determine if the execution is originating from a packaged `.jar` before assuming service mode is the default.

- **Alternative 1:** Run service setup regardless and fail silently. (Rejected: this would call `System.exit(0)` and terminate the Spring Boot application immediately when running inside an IDE).
- **Alternative 2 (Chosen):** Extract a helper method `isPackagedJar()` that checks the protection domain location and check this at the beginning of the `main()` method. If not running from a JAR, `runAsService` defaults to `false`.

### 2. Preventing Infinite Recursion in System Service Configuration
When the background service starts the application, the JVM must be invoked with `--no-service`. Otherwise, the background instance will attempt to register the service and exit immediately, causing systemd/LaunchAgent to fail or enter an infinite loop.

- **Choice:** Modify all autostart script generators to include `--no-service` at the end of the `java -jar` execution command:
  - **Linux Systemd:** `ExecStart=java -Djava.awt.headless=false -jar "..." --no-service`
  - **Windows Startup Batch:** `start javaw -jar "..." --no-service`
  - **macOS LaunchAgent Plist:** Add `<string>--no-service</string>` to the `ProgramArguments` array.

## Risks / Trade-offs

- **Risk:** Infinite restart loops if `--no-service` is missing from system configuration.
  - **Mitigation:** Ensure automated unit tests or local validation verify that `--no-service` is written to configuration templates.
