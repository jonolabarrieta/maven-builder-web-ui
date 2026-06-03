package net.olaba.mvnbuilder.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.olaba.mvnbuilder.service.SystemSettingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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
}
