package net.olaba.mvnbuilder.controller;

import net.olaba.mvnbuilder.entities.BuildProfile;
import net.olaba.mvnbuilder.repository.BuildProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for managing Maven build profiles.
 */
@Controller
@RequestMapping("/settings/profiles")
@RequiredArgsConstructor
public class BuildProfileController {

    private final BuildProfileRepository buildProfileRepository;

    /**
     * Retrieves the profile selector fragment. Creates a default profile if none
     * exists.
     * 
     * @param model The UI model.
     * @return The fragment name.
     */
    @GetMapping("/selector")
    public String getProfileSelector(final Model model) {
        List<BuildProfile> profiles = buildProfileRepository.findAll();
        if (profiles.isEmpty()) {
            // Create default profile if none exists
            final BuildProfile defaultProfile = BuildProfile.builder()
                    .withName("Default")
                    .withCommand("-B clean install")
                    .withIsDefault(true)
                    .build();
            buildProfileRepository.save(defaultProfile);
            profiles = List.of(defaultProfile);
        }

        final List<BuildProfile> finalProfiles = profiles;
        final BuildProfile activeProfile = profiles.stream()
                .filter(BuildProfile::isDefault)
                .findFirst()
                .orElse(finalProfiles.get(0));

        model.addAttribute("profiles", finalProfiles);
        model.addAttribute("activeProfile", activeProfile);
        return "fragments/profile-selector :: profile-selector-fragment";
    }

    /**
     * Sets a profile as the default one for future builds.
     * 
     * @param id The profile ID to activate.
     * @return A status message.
     */
    @PostMapping("/{id}/activate")
    @ResponseBody
    public String activateProfile(final @PathVariable Long id) {
        final List<BuildProfile> allProfiles = buildProfileRepository.findAll();
        for (final BuildProfile profile : allProfiles) {
            profile.setDefault(profile.getId().equals(id));
        }
        buildProfileRepository.saveAll(allProfiles);
        return "Activated";
    }

    /**
     * Adds a new build profile.
     * 
     * @param name    The profile name.
     * @param command The Maven command arguments.
     * @param model   The UI model.
     * @return The updated profile selector fragment.
     */
    @PostMapping("/add")
    public String addProfile(final @RequestParam String name, final @RequestParam String command, final Model model) {
        final BuildProfile newProfile = BuildProfile.builder()
                .withName(name)
                .withCommand(command)
                .withIsDefault(false)
                .build();
        buildProfileRepository.save(newProfile);
        return getProfileSelector(model);
    }

    /**
     * Deletes a build profile.
     * 
     * @param id    The profile ID to delete.
     * @param model The UI model.
     * @return The updated profile selector fragment.
     */
    @PostMapping("/{id}/delete")
    public String deleteProfile(final @PathVariable Long id, final Model model) {
        buildProfileRepository.deleteById(id);
        return getProfileSelector(model);
    }
}
