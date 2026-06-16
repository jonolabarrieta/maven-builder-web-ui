## ADDED Requirements

### Requirement: Disable Headless Mode
The application MUST run with Java AWT headless mode disabled (`java.awt.headless=false`) to support desktop environment GUI integration.

#### Scenario: Application startup without headless mode
- **WHEN** the application is started via the main class `MvnBuilderApplication`
- **THEN** Spring Boot starts in a non-headless context allowing AWT components to be instantiated

### Requirement: System Tray Icon Display
If the operating system supports a system tray, the application MUST create and display a system tray icon using the existing `/static/favicon.png` asset.

#### Scenario: Icon display on supported systems
- **WHEN** the application starts on a desktop environment with system tray support
- **THEN** the MvnBuilder icon is added to the system tray/status bar

### Requirement: System Tray Interaction Menu
The system tray icon MUST provide a popup menu containing options to open the web dashboard or exit the application.

#### Scenario: Open dashboard action
- **WHEN** the user double-clicks the system tray icon or selects "Abrir MvnBuilder" from the menu
- **THEN** the system default web browser opens `http://localhost:3333`

#### Scenario: Exit action
- **WHEN** the user selects the "Salir" option from the menu
- **THEN** the application automatically deletes the registered system service/startup script, shutdowns the Spring Boot context, and terminates the JVM process

#### Scenario: View logs action
- **WHEN** the user selects the "Ver log" option from the menu
- **THEN** a graphical window appears showing the application's runtime log entries updated in real-time

### Requirement: Automated Background Service Registration
The application MUST support command-line arguments to choose between running as a background service or running in the foreground attached to the terminal.

#### Scenario: Running in service mode with --service argument
- **WHEN** the application is started from a packaged JAR with the `--service` argument
- **THEN** it registers the system startup configurations, launches the background service/daemon instance, and the foreground process immediately terminates to free the terminal

#### Scenario: Running in normal mode without arguments
- **WHEN** the application is started from a packaged JAR without the `--service` argument
- **THEN** it runs in the foreground attached to the terminal, and no service/autostart configuration is generated or registered
