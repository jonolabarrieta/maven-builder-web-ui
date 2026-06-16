package net.olaba.mvnbuilder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.olaba.mvnbuilder.model.AssetInfo;
import net.olaba.mvnbuilder.model.UpdateInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Service for checking updates, parsing version information, and applying self-updates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateService {

    private final ObjectMapper objectMapper;

    @Value("${app.version:1.1.0}")
    private String currentVersion;

    @Value("${app.update.check-url}")
    private String updateCheckUrl;

    /**
     * Gets the configured current version.
     *
     * @return The version string.
     */
    public String getCurrentVersion() {
        return currentVersion;
    }

    /**
     * Checks the configured URL for updates.
     *
     * @return The UpdateInfo DTO from the response.
     * @throws Exception If checking fails.
     */
    public UpdateInfo checkUpdate() throws Exception {
        log.info("Checking for updates at URL: {}", updateCheckUrl);
        final HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(updateCheckUrl))
                .header("Accept", "application/json")
                .header("User-Agent", "MvnBuilder-Updater")
                .GET()
                .build();

        final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.error("Failed to check for updates. Status code: {}", response.statusCode());
            throw new RuntimeException("Failed to check for updates. Status code: " + response.statusCode());
        }

        return objectMapper.readValue(response.body(), UpdateInfo.class);
    }

    /**
     * Compares two SemVer version strings to see if latest is newer than current.
     *
     * @param currentVersion The current version.
     * @param latestVersion  The latest available version.
     * @return True if latest is newer, false otherwise.
     */
    public boolean isNewerVersion(final String currentVersion, final String latestVersion) {
        if (latestVersion == null || latestVersion.isBlank()) {
            return false;
        }
        final String cleanCurrent = currentVersion.replaceAll("^v", "").trim();
        final String cleanLatest = latestVersion.replaceAll("^v", "").trim();

        final String[] currentParts = cleanCurrent.split("\\.");
        final String[] latestParts = cleanLatest.split("\\.");

        final int length = Math.max(currentParts.length, latestParts.length);
        for (int i = 0; i < length; i++) {
            final int curr = i < currentParts.length ? parseOrZero(currentParts[i]) : 0;
            final int lat = i < latestParts.length ? parseOrZero(latestParts[i]) : 0;
            if (lat > curr) {
                return true;
            } else if (curr > lat) {
                return false;
            }
        }
        return false;
    }

    private int parseOrZero(final String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9]", ""));
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Identifies if the application is currently running as a JAR.
     *
     * @return True if running from a JAR, false otherwise (e.g. IDE or native image).
     */
    public boolean isJar() {
        try {
            final URL location = UpdateService.class.getProtectionDomain().getCodeSource().getLocation();
            return location.getPath().toLowerCase().endsWith(".jar");
        } catch (final Exception e) {
            log.error("Failed to detect execution mode: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Finds the best matching asset for the current platform and execution mode.
     *
     * @param updateInfo The release details.
     * @return The matched AssetInfo or null.
     */
    public AssetInfo findMatchingAsset(final UpdateInfo updateInfo) {
        if (updateInfo.assets() == null || updateInfo.assets().isEmpty()) {
            return null;
        }

        final boolean runningAsJar = isJar();
        if (runningAsJar) {
            return updateInfo.assets().stream()
                    .filter(a -> a.name().toLowerCase().endsWith(".jar"))
                    .findFirst()
                    .orElse(updateInfo.assets().get(0));
        }

        // Native binary: detect OS
        final String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return updateInfo.assets().stream()
                    .filter(a -> a.name().toLowerCase().endsWith(".exe") || a.name().toLowerCase().contains("win"))
                    .findFirst()
                    .orElse(updateInfo.assets().get(0));
        } else if (os.contains("mac")) {
            return updateInfo.assets().stream()
                    .filter(a -> a.name().toLowerCase().contains("mac") || a.name().toLowerCase().contains("osx") || a.name().toLowerCase().contains("darwin"))
                    .findFirst()
                    .orElse(updateInfo.assets().get(0));
        } else {
            return updateInfo.assets().stream()
                    .filter(a -> a.name().toLowerCase().contains("linux") || (!a.name().toLowerCase().contains("win") && !a.name().toLowerCase().contains("mac") && !a.name().toLowerCase().endsWith(".jar")))
                    .findFirst()
                    .orElse(updateInfo.assets().get(0));
        }
    }

    /**
     * Downloads an asset to a temporary file.
     *
     * @param downloadUrl The asset download URL.
     * @return The path to the downloaded temporary file.
     * @throws Exception If downloading fails.
     */
    public Path downloadAsset(final String downloadUrl) throws Exception {
        log.info("Downloading update from: {}", downloadUrl);
        final HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(downloadUrl))
                .GET()
                .build();

        final Path tempFile = Files.createTempFile("mvnbuilder-update-", ".tmp");
        final HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(tempFile));

        if (response.statusCode() != 200) {
            Files.deleteIfExists(tempFile);
            log.error("Failed to download update. HTTP status code: {}", response.statusCode());
            throw new RuntimeException("Failed to download update. HTTP status code: " + response.statusCode());
        }

        log.info("Download complete. Saved to: {}", tempFile);
        return response.body();
    }

    /**
     * Spawns a detached process to replace the running binary/JAR and restarts it.
     *
     * @param downloadedTempFile The downloaded file path.
     * @throws Exception If execution fails.
     */
    public void restartAndApplyUpdate(final Path downloadedTempFile) throws Exception {
        final URL location = UpdateService.class.getProtectionDomain().getCodeSource().getLocation();
        final String pathString = location.toURI().getPath();
        final boolean runningAsJar = isJar();

        String targetPath;
        String restartCmd;
        final long pid = ProcessHandle.current().pid();

        if (runningAsJar) {
            targetPath = new File(location.toURI()).getAbsolutePath();
            restartCmd = "java -jar \"" + targetPath + "\"";
        } else {
            targetPath = ProcessHandle.current().info().command().orElse(null);
            if (targetPath == null) {
                throw new IllegalStateException("Cannot determine running native binary path.");
            }
            restartCmd = "\"" + targetPath + "\"";
        }

        log.info("Preparing self-update script. Current PID: {}, Target File: {}, Restart Cmd: {}", pid, targetPath, restartCmd);

        final String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            createAndRunWindowsScript(pid, downloadedTempFile.toAbsolutePath().toString(), targetPath, restartCmd);
        } else {
            createAndRunUnixScript(pid, downloadedTempFile.toAbsolutePath().toString(), targetPath, restartCmd);
        }
    }

    private void createAndRunWindowsScript(final long pid, final String tempFilePath, final String targetFilePath, final String restartCmd) throws Exception {
        final Path scriptPath = Files.createTempFile("update-mvnbuilder-", ".bat");

        final String batContent = "@echo off\r\n" +
                ":loop\r\n" +
                "tasklist /fi \"PID eq " + pid + "\" 2>nul | find \"" + pid + "\" >nul\r\n" +
                "if %errorlevel% equ 0 (\r\n" +
                "    timeout /t 1 /nobreak >nul\r\n" +
                "    goto loop\r\n" +
                ")\r\n" +
                "move /y \"" + tempFilePath + "\" \"" + targetFilePath + "\"\r\n" +
                "start \"\" " + restartCmd + "\r\n" +
                "del \"%~f0\"\r\n";

        Files.writeString(scriptPath, batContent);
        log.info("Windows batch script created at: {}", scriptPath);

        new ProcessBuilder("cmd.exe", "/c", "start", "/b", scriptPath.toAbsolutePath().toString())
                .start();
    }

    private void createAndRunUnixScript(final long pid, final String tempFilePath, final String targetFilePath, final String restartCmd) throws Exception {
        final Path scriptPath = Files.createTempFile("update-mvnbuilder-", ".sh");

        final String shContent = "#!/bin/bash\n" +
                "while kill -0 " + pid + " 2>/dev/null; do\n" +
                "    sleep 0.5\n" +
                "done\n" +
                "mv \"" + tempFilePath + "\" \"" + targetFilePath + "\"\n" +
                "chmod +x \"" + targetFilePath + "\"\n" +
                "nohup " + restartCmd + " > /dev/null 2>&1 &\n" +
                "rm -- \"$0\"\n";

        Files.writeString(scriptPath, shContent);
        scriptPath.toFile().setExecutable(true);
        log.info("Unix shell script created at: {}", scriptPath);

        new ProcessBuilder("bash", scriptPath.toAbsolutePath().toString())
                .start();
    }
}
