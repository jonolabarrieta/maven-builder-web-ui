package net.olaba.mvnbuilder;

import net.olaba.mvnbuilder.gui.LogViewerFrame;
import net.olaba.mvnbuilder.model.BuildFailure;
import net.olaba.mvnbuilder.model.LogMessage;
import net.olaba.mvnbuilder.model.M2ProjectInfo;
import net.olaba.mvnbuilder.model.ProcessInfo;
import net.olaba.mvnbuilder.model.AssetInfo;
import net.olaba.mvnbuilder.model.UpdateInfo;
import net.olaba.mvnbuilder.service.FileSystemService;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;

import java.awt.*;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Main entry point for the MvnBuilder application.
 */
@SpringBootApplication
@EnableAsync
@RegisterReflectionForBinding({
    FileSystemService.FileItem.class,
    M2ProjectInfo.class,
    BuildFailure.class,
    LogMessage.class,
    ProcessInfo.class,
    AssetInfo.class,
    UpdateInfo.class
})
public class MvnBuilderApplication {

    /**
     * Main method to start the Spring Boot application.
     * 
     * @param args Command line arguments.
     */
    public static void main(final String[] args) {
        boolean runAsService = false;
        for (final String arg : args) {
            if ("--service".equalsIgnoreCase(arg) || "service".equalsIgnoreCase(arg) || "-service".equalsIgnoreCase(arg)) {
                runAsService = true;
                break;
            }
        }

        if (runAsService) {
            // Register, start as a background service/daemon, and immediately exit foreground process
            registerAndStartService();
            System.exit(0);
        }

        // Normal attached run mode
        final SpringApplicationBuilder builder = new SpringApplicationBuilder(MvnBuilderApplication.class);
        builder.headless(false);
        final ConfigurableApplicationContext context = builder.run(args);
        setupSystemTray(context);
    }

    /**
     * Attempts to register the application to start automatically in the background
     * on OS startup, if running from a packaged JAR file.
     */
    private static void registerAndStartService() {
        try {
            final URL location = MvnBuilderApplication.class.getProtectionDomain().getCodeSource().getLocation();
            String pathString = location.toString();

            // Truncate nested paths inside the JAR structure
            final int jarIndex = pathString.toLowerCase().indexOf(".jar");
            if (jarIndex != -1) {
                pathString = pathString.substring(0, jarIndex + 4);
            }

            // Strip protocol prefixes (nested:, file:, jar:file:)
            if (pathString.contains("nested:")) {
                pathString = pathString.substring(pathString.indexOf("nested:") + 7);
            }
            if (pathString.contains("file:")) {
                pathString = pathString.substring(pathString.indexOf("file:") + 5);
            }

            // Decode URL encoding (e.g. %20 for spaces)
            pathString = java.net.URLDecoder.decode(pathString, StandardCharsets.UTF_8);

            // Clean leading slash before drive letters on Windows (e.g. /C:/path -> C:/path)
            if (pathString.startsWith("/") && pathString.length() > 2 && pathString.charAt(2) == ':') {
                pathString = pathString.substring(1);
            }

            final File jarFile = new File(pathString);
            final String absolutePath = jarFile.getAbsolutePath();

            // Only register if running from a packaged JAR file
            if (!absolutePath.endsWith(".jar")) {
                System.out.println("Running in development/IDE mode (non-JAR). Skipping service registration.");
                return;
            }

            final String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                registerWindowsAutostart(absolutePath);
            } else if (os.contains("nix") || os.contains("nux")) {
                registerLinuxAutostart(absolutePath);
            } else if (os.contains("mac")) {
                registerMacAutostart(absolutePath);
            }
        } catch (final Exception e) {
            System.err.println("Failed to determine JAR path or register background service: " + e.getMessage());
        }
    }

    /**
     * Registers a Windows startup script in the user's Startup directory.
     */
    private static void registerWindowsAutostart(final String jarPath) {
        try {
            final String appData = System.getenv("APPDATA");
            if (appData == null) {
                return;
            }

            final Path startupDir = Paths.get(appData, "Microsoft", "Windows", "Start Menu", "Programs", "Startup");
            if (!Files.exists(startupDir)) {
                return;
            }

            final Path batFile = startupDir.resolve("start-mvnbuilder.bat");

            final String batContent = "@echo off\n" +
                    "start javaw -jar \"" + jarPath + "\"\n";

            Files.writeString(batFile, batContent);
            System.out.println("Windows startup script registered at: " + batFile);

            // Spawn the detached background process immediately
            runCommand("cmd.exe", "/c", "start", "javaw", "-jar", jarPath);
            System.out.println("Windows background service started.");
        } catch (final Exception e) {
            System.err.println("Failed to register Windows startup script: " + e.getMessage());
        }
    }

    /**
     * Registers a Linux systemd user service and enables it.
     */
    private static void registerLinuxAutostart(final String jarPath) {
        try {
            final String homeDir = System.getProperty("user.home");
            final Path serviceDir = Paths.get(homeDir, ".config", "systemd", "user");
            if (!Files.exists(serviceDir)) {
                Files.createDirectories(serviceDir);
            }

            final Path serviceFile = serviceDir.resolve("mvnbuilder.service");

            final String serviceContent = "[Unit]\n" +
                    "Description=MvnBuilder Web UI Service\n" +
                    "After=graphical-session.target\n\n" +
                    "[Service]\n" +
                    "Type=simple\n" +
                    "Environment=DISPLAY=:0\n" +
                    "ExecStart=java -Djava.awt.headless=false -jar \"" + jarPath + "\"\n" +
                    "Restart=on-failure\n\n" +
                    "[Install]\n" +
                    "WantedBy=default.target\n";

            Files.writeString(serviceFile, serviceContent);
            System.out.println("Created systemd user service file at: " + serviceFile);

            // Reload daemon, enable and start the service
            runCommand("systemctl", "--user", "daemon-reload");
            runCommand("systemctl", "--user", "enable", "mvnbuilder.service");
            runCommand("systemctl", "--user", "start", "mvnbuilder.service");
            System.out.println("Linux systemd user service registered, enabled, and started.");
        } catch (final Exception e) {
            System.err.println("Failed to register Linux systemd service: " + e.getMessage());
        }
    }

    /**
     * Registers a macOS LaunchAgent plist and bootstraps it.
     */
    private static void registerMacAutostart(final String jarPath) {
        try {
            final String homeDir = System.getProperty("user.home");
            final Path agentsDir = Paths.get(homeDir, "Library", "LaunchAgents");
            if (!Files.exists(agentsDir)) {
                Files.createDirectories(agentsDir);
            }

            final Path plistFile = agentsDir.resolve("net.olaba.mvnbuilder.plist");

            final String plistContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
                    "<plist version=\"1.0\">\n" +
                    "<dict>\n" +
                    "    <key>Label</key>\n" +
                    "    <string>net.olaba.mvnbuilder</string>\n" +
                    "    <key>ProgramArguments</key>\n" +
                    "    <array>\n" +
                    "        <string>java</string>\n" +
                    "        <string>-Djava.awt.headless=false</string>\n" +
                    "        <string>-Dapple.awt.UIElement=true</string>\n" +
                    "        <string>-jar</string>\n" +
                    "        <string>" + jarPath + "</string>\n" +
                    "    </array>\n" +
                    "    <key>RunAtLoad</key>\n" +
                    "    <true/>\n" +
                    "    <key>KeepAlive</key>\n" +
                    "    <true/>\n" +
                    "</dict>\n" +
                    "</plist>\n";

            Files.writeString(plistFile, plistContent);
            System.out.println("Created macOS LaunchAgent plist at: " + plistFile);

            final String domain = "gui/" + getMacUid();
            runCommand("launchctl", "bootstrap", domain, plistFile.toAbsolutePath().toString());
            runCommand("launchctl", "kickstart", "-p", domain + "/net.olaba.mvnbuilder");
            System.out.println("macOS LaunchAgent registered and started.");
        } catch (final Exception e) {
            System.err.println("Failed to register macOS LaunchAgent: " + e.getMessage());
        }
    }

    private static String getMacUid() {
        try {
            final Process process = new ProcessBuilder("id", "-u").start();
            try (final InputStream in = process.getInputStream()) {
                return new String(in.readAllBytes()).trim();
            }
        } catch (final Exception e) {
            return "501";
        }
    }

    private static void runCommand(final String... cmd) {
        try {
            final Process process = new ProcessBuilder(cmd).start();
            final int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("Command failed with exit code " + exitCode + ": " + String.join(" ", cmd));
            }
        } catch (final Exception e) {
            System.err.println("Exception running command: " + String.join(" ", cmd) + " - " + e.getMessage());
        }
    }

    /**
     * Cleans up and unregisters background services / startup scripts.
     */
    private static void cleanupBackgroundService() {
        try {
            final String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                cleanupWindowsAutostart();
            } else if (os.contains("nix") || os.contains("nux")) {
                cleanupLinuxAutostart();
            } else if (os.contains("mac")) {
                cleanupMacAutostart();
            }
        } catch (final Exception e) {
            System.err.println("Failed to clean up background service: " + e.getMessage());
        }
    }

    private static void cleanupLinuxAutostart() {
        try {
            final String homeDir = System.getProperty("user.home");
            final Path serviceFile = Paths.get(homeDir, ".config", "systemd", "user", "mvnbuilder.service");
            if (Files.exists(serviceFile)) {
                runCommand("systemctl", "--user", "disable", "mvnbuilder.service");
                Files.delete(serviceFile);
                runCommand("systemctl", "--user", "daemon-reload");
                System.out.println("Linux systemd user service removed and disabled.");
            }
        } catch (final Exception e) {
            System.err.println("Failed to cleanup Linux systemd service: " + e.getMessage());
        }
    }

    private static void cleanupWindowsAutostart() {
        try {
            final String appData = System.getenv("APPDATA");
            if (appData != null) {
                final Path batFile = Paths.get(appData, "Microsoft", "Windows", "Start Menu", "Programs", "Startup", "start-mvnbuilder.bat");
                if (Files.exists(batFile)) {
                    Files.delete(batFile);
                    System.out.println("Windows startup script removed.");
                }
            }
        } catch (final Exception e) {
            System.err.println("Failed to cleanup Windows startup script: " + e.getMessage());
        }
    }

    private static void cleanupMacAutostart() {
        try {
            final String homeDir = System.getProperty("user.home");
            final Path plistFile = Paths.get(homeDir, "Library", "LaunchAgents", "net.olaba.mvnbuilder.plist");
            if (Files.exists(plistFile)) {
                runCommand("launchctl", "bootout", "gui/" + getMacUid(), plistFile.toAbsolutePath().toString());
                Files.delete(plistFile);
                System.out.println("macOS LaunchAgent plist removed and unloaded.");
            }
        } catch (final Exception e) {
            System.err.println("Failed to cleanup macOS LaunchAgent: " + e.getMessage());
        }
    }

    /**
     * Set up the system tray icon and its popup menu if supported by the OS and environment.
     * 
     * @param context The Spring application context.
     */
    private static void setupSystemTray(final ConfigurableApplicationContext context) {
        if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
            System.out.println("SystemTray is not supported on this platform/environment.");
            return;
        }

        try {
            final SystemTray tray = SystemTray.getSystemTray();

            // Load favicon.png from the classpath resources (static/favicon.png)
            final URL imageURL = MvnBuilderApplication.class.getResource("/static/favicon.png");
            if (imageURL == null) {
                System.err.println("SystemTray icon '/static/favicon.png' not found.");
                return;
            }
            final Image image = Toolkit.getDefaultToolkit().getImage(imageURL);

            final PopupMenu popup = new PopupMenu();

            final MenuItem openItem = new MenuItem("Abrir MvnBuilder");
            openItem.addActionListener(e -> openBrowser());

            final MenuItem logItem = new MenuItem("Ver log");
            logItem.addActionListener(e -> {
                EventQueue.invokeLater(() -> {
                    try {
                        final LogViewerFrame logFrame = new LogViewerFrame();
                        logFrame.setVisible(true);
                    } catch (final Exception ex) {
                        System.err.println("Failed to open log viewer: " + ex.getMessage());
                    }
                });
            });

            final MenuItem exitItem = new MenuItem("Salir");
            exitItem.addActionListener(e -> {
                cleanupBackgroundService();
                SpringApplication.exit(context, () -> 0);
                System.exit(0);
            });

            popup.add(openItem);
            popup.add(logItem);
            popup.addSeparator();
            popup.add(exitItem);

            final TrayIcon trayIcon = new TrayIcon(image, "MvnBuilder", popup);
            trayIcon.setImageAutoSize(true);

            // Double click on the tray icon also opens the browser
            trayIcon.addActionListener(e -> openBrowser());

            tray.add(trayIcon);
        } catch (final Exception e) {
            System.err.println("Failed to initialize SystemTray: " + e.getMessage());
        }
    }

    /**
     * Opens the default browser to the application home page (http://localhost:3333).
     */
    private static void openBrowser() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI("http://localhost:3333"));
            } else {
                System.err.println("Desktop browsing is not supported.");
            }
        } catch (final Exception ex) {
            System.err.println("Failed to open browser: " + ex.getMessage());
        }
    }

}


