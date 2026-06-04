package net.olaba.mvnbuilder.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.olaba.mvnbuilder.entities.JavaInstallation;
import net.olaba.mvnbuilder.entities.SystemSetting;
import net.olaba.mvnbuilder.repository.JavaInstallationRepository;
import net.olaba.mvnbuilder.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing global system settings and Java installations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemSettingService {

    private final SystemSettingRepository systemSettingRepository;
    private final JavaInstallationRepository javaInstallationRepository;

    /**
     * Retrieves the global system settings. If none exist, a default setting with ID 1
     * is created and saved.
     * 
     * @return The SystemSetting configuration.
     */
    public SystemSetting getSettings() {
        return systemSettingRepository.findById(1L).orElseGet(() -> {
            // Note: Use manual setter for ID since Builder might ignore it if ID is auto-generated in schema
            SystemSetting defaultSettings = SystemSetting.builder()
                    .withFavoritePath("")
                    .build();
            try {
                // Try saving. Depending on JPA configuration, setting ID manually might require saveAndFlush or merge.
                // Since this is H2 update mode and GenerationType.IDENTITY, we can just save it.
                // We'll set the ID to 1.
                defaultSettings.setId(1L);
                return systemSettingRepository.save(defaultSettings);
            } catch (Exception e) {
                log.error("Failed to create default system settings: {}", e.getMessage());
                return defaultSettings;
            }
        });
    }

    /**
     * Updates the favorite directory path configuration.
     * 
     * @param favoritePath The new path.
     * @return The updated SystemSetting.
     */
    @Transactional
    public SystemSetting updateFavoritePath(final String favoritePath) {
        final SystemSetting settings = getSettings();
        settings.setFavoritePath(favoritePath != null ? favoritePath.trim() : "");
        return systemSettingRepository.save(settings);
    }

    /**
     * Registers a new Java installation. Automatically sets as default if it's the first one.
     * 
     * @param name     The descriptive name.
     * @param javaHome The directory path.
     * @return The registered JavaInstallation.
     */
    @Transactional
    public JavaInstallation addJavaInstallation(final String name, final String javaHome) {
        final boolean isFirst = javaInstallationRepository.count() == 0;
        final JavaInstallation installation = JavaInstallation.builder()
                .withName(name.trim())
                .withJavaHome(javaHome.trim())
                .withIsDefault(isFirst)
                .build();
        return javaInstallationRepository.save(installation);
    }

    /**
     * Lists all registered Java installations.
     * 
     * @return A list of JavaInstallation objects.
     */
    public List<JavaInstallation> getAllJavaInstallations() {
        return javaInstallationRepository.findAll();
    }

    /**
     * Deletes a Java installation.
     * 
     * @param id The installation ID.
     */
    @Transactional
    public void deleteJavaInstallation(final Long id) {
        javaInstallationRepository.deleteById(id);
    }

    /**
     * Sets a Java installation as the active default one, deselecting others.
     * 
     * @param id The installation ID.
     */
    @Transactional
    public void setDefaultJavaInstallation(final Long id) {
        final List<JavaInstallation> installations = javaInstallationRepository.findAll();
        for (final JavaInstallation inst : installations) {
            inst.setDefault(inst.getId().equals(id));
        }
        javaInstallationRepository.saveAll(installations);
    }

    /**
     * Gets the default active Java installation, if any.
     * 
     * @return An Optional containing the default active JavaInstallation.
     */
    public Optional<JavaInstallation> getDefaultJavaInstallation() {
        return javaInstallationRepository.findByIsDefaultTrue();
    }
}
