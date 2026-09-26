package net.olaba.mvnbuilder.service;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Launches local desktop applications at a validated project directory.
 */
@Service
public class DesktopLauncherService {

    /**
     * Opens a project directory in Visual Studio Code.
     *
     * @param projectDirectory directory to open
     * @throws IOException when the VS Code command cannot be started
     */
    public void openVsCode(final File projectDirectory) throws IOException {
        new ProcessBuilder(vsCodeCommand(currentOs(), projectDirectory.getAbsolutePath())).start();
    }

    /**
     * Opens a project directory in the native file explorer.
     *
     * @param projectDirectory directory to reveal
     * @throws IOException when the file explorer cannot be started
     */
    public void openExplorer(final File projectDirectory) throws IOException {
        new ProcessBuilder(explorerCommand(currentOs(), projectDirectory.getAbsolutePath())).start();
    }

    /**
     * Opens a terminal whose working directory is the supplied project directory.
     *
     * @param projectDirectory existing project directory
     * @throws IOException when no supported terminal launcher can be started
     */
    public void openTerminal(final File projectDirectory) throws IOException {
        if (!projectDirectory.isDirectory()) {
            throw new IOException("Project directory does not exist: " + projectDirectory.getAbsolutePath());
        }

        IOException lastError = null;
        for (final List<String> command : terminalCommands(currentOs(), projectDirectory.getAbsolutePath())) {
            try {
                new ProcessBuilder(command).start();
                return;
            } catch (final IOException error) {
                lastError = error;
            }
        }

        throw new IOException("No supported terminal launcher is available for this system.", lastError);
    }

    /**
     * Returns the OS-specific Visual Studio Code command.
     *
     * @param os operating system name
     * @param path project directory path
     * @return command arguments
     */
    List<String> vsCodeCommand(final String os, final String path) {
        if (isWindows(os)) {
            return List.of("cmd.exe", "/c", "code", path);
        }
        return List.of("code", path);
    }

    /**
     * Returns the OS-specific native file explorer command.
     *
     * @param os operating system name
     * @param path project directory path
     * @return command arguments
     */
    List<String> explorerCommand(final String os, final String path) {
        if (isWindows(os)) {
            return List.of("explorer.exe", path);
        }
        if (isMac(os)) {
            return List.of("open", path);
        }
        return List.of("xdg-open", path);
    }

    /**
     * Returns ordered terminal launcher attempts for the given operating system.
     *
     * @param os operating system name
     * @param path project directory path
     * @return launcher commands in fallback order
     */
    List<List<String>> terminalCommands(final String os, final String path) {
        if (isWindows(os)) {
            return List.of(List.of("cmd.exe", "/c", "start", "", "cmd.exe", "/K", "cd", "/d", path));
        }
        if (isMac(os)) {
            return List.of(List.of("open", "-a", "Terminal", path));
        }
        return List.of(
                List.of("xdg-terminal-exec", "--working-directory", path),
                List.of("gnome-terminal", "--working-directory=" + path),
                List.of("konsole", "--workdir", path),
                List.of("xfce4-terminal", "--working-directory=" + path));
    }

    /**
     * Obtains the current operating system name in lowercase form.
     *
     * @return normalized operating system name
     */
    private String currentOs() {
        return System.getProperty("os.name", "").toLowerCase();
    }

    /**
     * Determines whether an operating system is Windows.
     *
     * @param os normalized operating system name
     * @return true for Windows
     */
    private boolean isWindows(final String os) {
        return os.contains("win");
    }

    /**
     * Determines whether an operating system is macOS.
     *
     * @param os normalized operating system name
     * @return true for macOS
     */
    private boolean isMac(final String os) {
        return os.contains("mac");
    }
}
