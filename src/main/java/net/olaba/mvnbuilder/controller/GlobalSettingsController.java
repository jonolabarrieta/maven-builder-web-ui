package net.olaba.mvnbuilder.controller;

import net.olaba.mvnbuilder.entities.FavoriteM2Folder;
import net.olaba.mvnbuilder.repository.FavoriteM2FolderRepository;
import net.olaba.mvnbuilder.service.MavenRepositoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for managing global settings, specifically favorite M2 repository
 * folders.
 */
@Controller
@RequestMapping("/settings/m2-favorites")
@RequiredArgsConstructor
@Slf4j
public class GlobalSettingsController {

    private final FavoriteM2FolderRepository favoriteM2FolderRepository;
    private final MavenRepositoryService mavenRepositoryService;

    /**
     * Lists all favorite M2 folders.
     * 
     * @param model The UI model.
     * @return The fragment name.
     */
    @GetMapping
    public String getFavorites(final Model model) {
        model.addAttribute("favorites", favoriteM2FolderRepository.findAll());
        return "fragments/m2-favorites :: m2-favorites-list";
    }

    /**
     * Adds a new group ID to the favorites list.
     * 
     * @param groupId The group ID to add.
     * @param model   The UI model.
     * @return The updated favorites fragment.
     */
    @PostMapping("/add")
    public String addFavorite(final @RequestParam String groupId, final Model model) {
        if (groupId == null || groupId.trim().isEmpty()) {
            return getFavorites(model);
        }

        try {
            final FavoriteM2Folder folder = FavoriteM2Folder.builder()
                    .withGroupId(groupId.trim())
                    .build();
            favoriteM2FolderRepository.save(folder);
        } catch (final Exception e) {
            log.error("Failed to add M2 favorite: {}", e.getMessage());
        }

        return getFavorites(model);
    }

    /**
     * Removes a group ID from the favorites list.
     * 
     * @param id    The favorite ID.
     * @param model The UI model.
     * @return The updated favorites fragment.
     */
    @PostMapping("/{id}/delete")
    public String deleteFavorite(final @PathVariable Long id, final Model model) {
        favoriteM2FolderRepository.deleteById(id);
        return getFavorites(model);
    }

    /**
     * Physically deletes the files in the M2 repository for a favorite group ID.
     * 
     * @param id The favorite ID.
     * @return A status message.
     */
    @PostMapping("/{id}/clear-m2")
    @ResponseBody
    public String clearM2Folder(final @PathVariable Long id) {
        final FavoriteM2Folder folder = favoriteM2FolderRepository.findById(id).orElseThrow();
        final boolean deleted = mavenRepositoryService.deleteByGroupId(folder.getGroupId());

        if (deleted) {
            log.info("Successfully deleted M2 folder: {}", folder.getGroupId());
            return "Cleared successfully";
        }

        return "Folder not found or failed to delete";
    }
}
