## Why

Currently, when the application is launched from a packaged JAR, it runs in the foreground attached to the terminal by default. To register and start it as a background service/daemon, the user must explicitly pass the `--service` command-line argument. Changing the default behavior so that it launches as a background service by default, unless `--no-service` is passed, will provide a smoother out-of-the-box experience for end users who want the application to automatically run in the background and configure itself for startup autostart.

## What Changes

- **Change Default Launch Behavior**: Running a packaged JAR without arguments will now default to registering and starting the background service/daemon.
- **Introduce `--no-service`**: Introduce a `--no-service` (along with `no-service` and `-no-service`) command-line argument to allow running the application in the foreground attached to the terminal.
- **Update Autostart Configurations**: The generated startup/service configurations (systemd unit, Windows batch file, macOS LaunchAgent) must now pass the `--no-service` argument to prevent infinite recursion when system services execute the JAR.

## Capabilities

### New Capabilities

*None*

### Modified Capabilities

- `system-tray-integration`: Update JAR execution logic to default to service registration/daemon mode, and use `--no-service` for foreground attached execution.

## Impact

- **MvnBuilderApplication.java**: Update argument parsing and launch logic.
- **Autostart/Startup Scripts**: Modify Linux systemd configuration generator, Windows `.bat` generator, and macOS plist generator to include `--no-service`.
- **System Tray Integration Spec**: Update scenarios to reflect the new default behavior and the new `--no-service` parameter.
