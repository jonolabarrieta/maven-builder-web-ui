## Why

Users want the MvnBuilder application to run as a persistent background desktop service. Currently, starting the application requires a persistent terminal session, and there is no easy desktop integration to monitor its status, open the dashboard, or shut down the process cleanly without command-line intervention. Setting up system services manually is complex and error-prone for users.

## What Changes

- **Automatic Service Registration (`--service` parameter):** When executed with the `--service` (or `service`) command-line argument, the application automatically detects its OS, writes the service/autostart configurations, enables and starts the background service/daemon, and exits the terminal immediately.
- **Normal Foreground Mode (No parameters):** Running without arguments keeps the application attached to the terminal (no service registration occurs), and it closes if the terminal is terminated.
- **System Tray Icon (TrayIcon):** Boot the application with Spring Boot's headless mode disabled (`headless(false)`) and display a status indicator icon in the OS system tray/status bar using `java.awt.SystemTray`.
- **System Tray Context Menu:** Create a popup menu to easily "Abrir MvnBuilder" (launches default web browser), "Ver log" (opens a native graphical window displaying live application logs), or "Salir" (safely stops Spring Boot, cleans up service files, and terminates the JVM).

## Capabilities

### New Capabilities
- `system-tray-integration`: Handles system tray icon creation, interaction menus (with live log viewing window), headless-mode disabling, and automated background startup registration/service setup (via `--service` parameter) or standard foreground run (no arguments) for Windows, Linux, and macOS.

### Modified Capabilities

## Impact

- **MvnBuilderApplication.java & LogViewerFrame.java:** Main entry point class will be updated to parse arguments, handle `--service` delegation, and initialize the SystemTray. A new Swing class `LogViewerFrame` will be created to render the log window.
- **application.properties:** Configured to write application logs to a file in the user's home directory (`${user.home}/.mvnbuilder/mvnbuilder.log`) for retrieval by the log viewer.
- **Configuration & Build:** No new runtime Maven dependencies are added (uses standard AWT/Swing and Java NIO APIs). Requires display-session access when launched as a service/daemon.
