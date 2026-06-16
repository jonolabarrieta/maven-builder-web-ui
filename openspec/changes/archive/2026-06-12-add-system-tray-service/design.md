## Context

The MvnBuilder application is currently designed as a standalone Spring Boot web application. While it is accessible via the browser at `http://localhost:3333`, running it requires leaving a terminal window open. To make it behave like a native desktop utility, we want to integrate it into the operating system's system tray (menu bar / status indicator) and allow it to run in the background (as a daemon or service) without a console window.

## Goals / Non-Goals

**Goals:**
- Boot the Spring Boot application in a non-headless GUI mode (`headless(false)`).
- Display a tray icon in the OS system tray using the existing `favicon.png` asset.
- Implement a double-click action and a popup menu with "Open MvnBuilder", "Ver log", and "Salir" actions.
- Terminate the Spring Boot process cleanly when "Salir" is selected.
- Automatically register the background service/startup scripts on Windows, Linux (systemd), and macOS on the application's first execution from a compiled JAR.
- Render a live-updating GUI log viewer in a Swing window when "Ver log" is selected.

**Non-Goals:**
- Rewriting the entire application as a desktop GUI app (e.g. JavaFX/Swing frontend). The UI remains web-based (Thymeleaf/HTMX).
- Packaging the application as a native image (GraalVM compilation is supported by the pom but out of scope for the tray icon logic itself).
- Requiring administrative privileges (`sudo` / Administrator) for service registration. Everything runs in user-space.

## Decisions

### Decision 1: AWT SystemTray vs. Third-Party Library (e.g., Dorkbox SystemTray)
- **Option A (Chosen):** Use Java's standard `java.awt.SystemTray` and `java.awt.TrayIcon`.
  - *Rationale:* It requires zero external dependencies, adding no weight or complexity to the `pom.xml`. Since Java 21 is used, the built-in AWT API works reliably across Windows and macOS, and works on Linux if the desktop environment supports AppIndicators.
- **Option B:** Add a library like `com.dorkbox:SystemTray`.
  - *Rationale for rejection:* Although it offers better native GTK integration on some Linux distributions, it introduces external binaries and dependencies that could complicate GraalVM native image generation (which the project supports via native-maven-plugin).

### Decision 2: Disabling Spring Boot Headless Mode
- **Choice:** Modify `MvnBuilderApplication.java` main method to disable headless mode using `SpringApplicationBuilder.headless(false)`.
  - *Rationale:* Without this, AWT class loading will trigger `HeadlessException` immediately upon startup. We will wrap the tray initialization to check if `GraphicsEnvironment.isHeadless()` is true or if `SystemTray.isSupported()` is false, ensuring the web application still runs successfully on server/headless environments without crashing.

### Decision 3: Background execution method per OS
- **Windows:** Use `javaw -jar ...` inside a `.bat` file launched via the user's Startup folder.
  - *Rationale:* Simple, native, requires no extra tools, and hides the command prompt window.
- **Linux:** Create a systemd user unit (`~/.config/systemd/user/mvnbuilder.service`).
  - *Rationale:* Properly manages the lifecycle of the service per user session and handles display server mapping (`DISPLAY=:0`).
- **macOS:** Create a LaunchAgent `plist` with the flag `-Dapple.awt.UIElement=true`.
  - *Rationale:* Ensures the Java process behaves as a background agent (only showing in the menu bar, not in the Dock).

### Decision 4: Zero-Configuration Autostart / Self-Registration
- **Choice:** Gate service self-registration and daemon execution behind the `--service` (or `service` / `-service`) command-line argument.
  - *With `--service`:* Upon startup, the foreground process checks if it is running from a packaged `.jar` file. If yes, it writes the configuration files (systemd user unit on Linux, startup shortcut on Windows, plist LaunchAgent on macOS), registers/enables them for autostart, **starts** the service in the background (or spawns it detached), and **immediately exits the foreground process**, releasing the terminal.
  - *Without `--service` (Normal Mode):* The application boots directly in the foreground, staying attached to the terminal. If the terminal is closed, the process closes.
  - *JAR Detection:* Check `MvnBuilderApplication.class.getProtectionDomain().getCodeSource().getLocation()` to get the current executable location. If it is a classes folder (IDE runtime), skip registration to avoid polluting the development machine.
  - *Linux Command Execution:* Call `ProcessBuilder` programmatically to run `systemctl --user daemon-reload`, `systemctl --user enable mvnbuilder.service`, and `systemctl --user start mvnbuilder.service` to spawn the background daemon instance, then exit.

### Decision 5: Real-Time Log Viewer UI
- **Choice:** Configure logging in Spring Boot to output to a file in the user home directory (`~/.mvnbuilder/mvnbuilder.log`).
- **Tailing logs:** When the log viewer is opened, a Swing `JFrame` is initialized. It reads the existing file contents and launches a Swing Timer running every second. The timer compares the current file length with the last read offset, reads the delta bytes via a `RandomAccessFile`, decodes them as UTF-8, and appends them to a scroll-locked `JTextArea`.

### Decision 6: Autostart Cleanup on Exit
- **Choice:** When the user selects 'Salir' from the system tray popup menu, the application deletes the written startup/service files and unregisters the services programmatically before closing down the JVM.
  - *Linux:* Deletes `~/.config/systemd/user/mvnbuilder.service` and executes `systemctl --user disable mvnbuilder.service` followed by `systemctl --user daemon-reload`.
  - *Windows:* Deletes `start-mvnbuilder.bat` from the Startup folder.
  - *macOS:* Deletes `~/Library/LaunchAgents/net.olaba.mvnbuilder.plist` and calls `launchctl bootout gui/$(id -u)`.
  - *Rationale:* Ensures that shutting down the application via the UI cleans up autostart paths so that the background service does not run on subsequent boots unless launched manually again.

## Risks / Trade-offs

- **[Risk] GNOME Shell doesn't display tray icons by default:** GNOME Shell (Linux) deprecated standard tray icons.
  - *Mitigation:* Document that Linux users running GNOME MUST enable the **AppIndicator and KStatusNotifierItem Support** extension (pre-installed on Ubuntu, but manual on Fedora/Arch).
- **[Risk] Resource loading in Jar/Native Image:** Locating the icon file path dynamically might fail if the app is packaged as a JAR or compiled as a GraalVM native image.
  - *Mitigation:* Load the icon using the classpath resource stream (`MvnBuilderApplication.class.getResource("/static/favicon.png")`) or use a toolkit image loader that handles resources inside jar files.
