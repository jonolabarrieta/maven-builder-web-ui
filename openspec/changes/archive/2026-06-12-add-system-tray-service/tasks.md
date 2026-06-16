## 1. Disable Headless Mode and Setup SystemTray

- [x] 1.1 Modify `MvnBuilderApplication.java` main method to disable headless mode using `SpringApplicationBuilder.headless(false)`.
- [x] 1.2 Implement the helper method `setupSystemTray(ConfigurableApplicationContext context)` in `MvnBuilderApplication.java` with validation checks for `GraphicsEnvironment.isHeadless()` and `SystemTray.isSupported()`.

## 2. Implement SystemTray Interactions

- [x] 2.1 Load the existing `/static/favicon.png` asset as the tray icon image from the classpath.
- [x] 2.2 Create the tray `PopupMenu` with "Abrir MvnBuilder" and "Salir" menu items.
- [x] 2.3 Implement the action listener for "Abrir MvnBuilder" (and the tray icon double-click action) to launch the default browser targeting `http://localhost:3333`.
- [x] 2.4 Implement the action listener for "Salir" to stop the Spring Boot application context cleanly and terminate the process.

## 3. Background Service Documentation and Scripts

- [x] 3.1 Create a `service-templates` directory in the project root containing startup scripts.
- [x] 3.2 Add `start-mvnbuilder.bat` helper script for Windows Startup execution.
- [x] 3.3 Add `mvnbuilder.service` systemd service template for Linux user-session execution.
- [x] 3.4 Add `net.olaba.mvnbuilder.plist` LaunchAgent template for macOS execution.

## 4. Automated Service Registration

- [x] 4.1 Implement a check to determine if the application is running from a packaged JAR and resolve its absolute path.
- [x] 4.2 Create Linux systemd service self-registration logic (writing the service unit to `~/.config/systemd/user` and calling `systemctl` commands).
- [x] 4.3 Create Windows startup script self-registration logic (writing the bat script to the user's Startup directory).
- [x] 4.4 Create macOS LaunchAgent self-registration logic (writing the plist file to `~/Library/LaunchAgents` and loading it using `launchctl`).
- [x] 4.5 Call the self-registration method on application startup inside `MvnBuilderApplication.java`.

## 5. Live Log Viewer Window

- [x] 5.1 Configure logback/logging in `application.properties` to write logs to a file in the user's home directory (`~/.mvnbuilder/mvnbuilder.log`).
- [x] 5.2 Create the Swing graphical class `LogViewerFrame.java` displaying log contents.
- [x] 5.3 Implement tailing logic inside `LogViewerFrame` that reads file appends on a timer and updates the textarea.
- [x] 5.4 Add the "Ver log" menu option to the SystemTray popup menu and open `LogViewerFrame` on click.

## 6. Autostart Cleanup on Exit

- [x] 6.1 Implement service deregistration logic for Linux, Windows, and macOS that removes autostart files and runs cleanup commands.
- [x] 6.2 Bind the service deregistration logic to the "Salir" menu action in `MvnBuilderApplication.java` before calling `SpringApplication.exit()`.

## 7. Command-Line Parameter Gating

- [x] 7.1 Modify the `main` method in `MvnBuilderApplication.java` to parse arguments for `--service`, `service`, or `-service` options.
- [x] 7.2 Gate `registerBackgroundService()` execution to run only when the service parameter is detected.
- [x] 7.3 If running with the service parameter, execute service start commands programmatically to launch the daemon, and immediately exit the current foreground process.
