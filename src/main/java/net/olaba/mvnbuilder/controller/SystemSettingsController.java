package net.olaba.mvnbuilder.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.olaba.mvnbuilder.model.AssetInfo;
import net.olaba.mvnbuilder.model.UpdateInfo;
import net.olaba.mvnbuilder.service.SystemSettingService;
import net.olaba.mvnbuilder.service.UpdateService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;

/**
 * Controller for managing global system settings, including favorite directory paths
 * and JDK registrations.
 */
@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
@Slf4j
public class SystemSettingsController {

    private final SystemSettingService systemSettingService;
    private final UpdateService updateService;

    /**
     * Renders the global settings and configuration screen.
     * 
     * @param model The UI model.
     * @return The settings view name.
     */
    @GetMapping
    public String getSettingsPage(final Model model) {
        model.addAttribute("settings", systemSettingService.getSettings());
        model.addAttribute("javaInstallations", systemSettingService.getAllJavaInstallations());
        model.addAttribute("currentVersion", updateService.getCurrentVersion());
        return "settings";
    }

    /**
     * Updates the global favorite path.
     * 
     * @param favoritePath The new path.
     * @return Redirect to the settings page.
     */
    @PostMapping("/favorite-path")
    public String updateFavoritePath(final @RequestParam(required = false) String favoritePath) {
        log.info("Updating favorite path to: {}", favoritePath);
        systemSettingService.updateFavoritePath(favoritePath);
        return "redirect:/settings";
    }

    /**
     * Adds a new Java installation.
     * 
     * @param name     The descriptive name (e.g. JDK 17).
     * @param javaHome The directory path.
     * @return Redirect to the settings page.
     */
    @PostMapping("/java-installations/add")
    public String addJavaInstallation(final @RequestParam String name, final @RequestParam String javaHome) {
        log.info("Adding Java installation: {} ({})", name, javaHome);
        if (name == null || name.trim().isEmpty() || javaHome == null || javaHome.trim().isEmpty()) {
            return "redirect:/settings";
        }
        try {
            systemSettingService.addJavaInstallation(name, javaHome);
        } catch (final Exception e) {
            log.error("Failed to add Java installation: {}", e.getMessage());
        }
        return "redirect:/settings";
    }

    /**
     * Deletes a Java installation.
     * 
     * @param id The installation ID to delete.
     * @return Redirect to the settings page.
     */
    @PostMapping("/java-installations/{id}/delete")
    public String deleteJavaInstallation(final @PathVariable Long id) {
        log.info("Deleting Java installation ID: {}", id);
        try {
            systemSettingService.deleteJavaInstallation(id);
        } catch (final Exception e) {
            log.error("Failed to delete Java installation: {}", e.getMessage());
        }
        return "redirect:/settings";
    }

    /**
     * Marks a Java installation as the default active one.
     * 
     * @param id The installation ID.
     * @return Redirect to the settings page.
     */
    @PostMapping("/java-installations/{id}/default")
    public String setDefaultJavaInstallation(final @PathVariable Long id) {
        log.info("Setting Java installation ID {} as default active", id);
        try {
            systemSettingService.setDefaultJavaInstallation(id);
        } catch (final Exception e) {
            log.error("Failed to set default Java installation: {}", e.getMessage());
        }
        return "redirect:/settings";
    }

    /**
     * Manually checks for updates and returns the updater UI fragment.
     * 
     * @param model The UI model.
     * @return The ThymeLeaf updater-card fragment.
     */
    @PostMapping("/check-update")
    public String checkUpdate(final Model model) {
        final String current = updateService.getCurrentVersion();
        model.addAttribute("currentVersion", current);
        try {
            final UpdateInfo updateInfo = updateService.checkUpdate();
            final boolean newer = updateService.isNewerVersion(current, updateInfo.tagName());
            model.addAttribute("updateAvailable", newer);
            model.addAttribute("latestVersion", updateInfo.tagName());
            model.addAttribute("releaseNotes", updateInfo.body());
            
            final AssetInfo asset = updateService.findMatchingAsset(updateInfo);
            model.addAttribute("matchingAsset", asset);
            model.addAttribute("updateChecked", true);
        } catch (final Exception e) {
            log.error("Failed to check for updates: {}", e.getMessage());
            model.addAttribute("updateCheckError", "No se pudo comprobar la actualización: " + e.getMessage());
            model.addAttribute("updateChecked", true);
        }
        return "settings :: updater-card";
    }

    /**
     * Downloads and applies the update, initiating process restart.
     * 
     * @param downloadUrl The asset download URL.
     * @return HTML status response.
     */
    @PostMapping("/apply-update")
    @ResponseBody
    public String applyUpdate(final @RequestParam String downloadUrl) {
        log.info("Triggering update download from URL: {}", downloadUrl);
        try {
            final Path tempFile = updateService.downloadAsset(downloadUrl);
            updateService.restartAndApplyUpdate(tempFile);
            return "<div class=\"p-4 mb-4 text-sm text-green-700 bg-green-50 border border-green-200 rounded-xl font-medium\">" +
                   "Actualización descargada con éxito. Reiniciando la aplicación..." +
                   "</div>";
        } catch (final Exception e) {
            log.error("Failed to apply update: {}", e.getMessage());
            return "<div class=\"p-4 mb-4 text-sm text-red-700 bg-red-50 border border-red-200 rounded-xl font-medium\">" +
                   "Error al aplicar la actualización: " + e.getMessage() +
                   "</div>";
        }
    }
}
