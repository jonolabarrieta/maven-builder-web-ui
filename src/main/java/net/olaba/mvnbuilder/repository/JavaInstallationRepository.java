package net.olaba.mvnbuilder.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import net.olaba.mvnbuilder.entities.JavaInstallation;
import java.util.Optional;

/**
 * Repository interface for JavaInstallation entity operations.
 */
@Repository
public interface JavaInstallationRepository extends JpaRepository<JavaInstallation, Long> {
    /**
     * Finds the Java installation marked as the default active version.
     * 
     * @return An Optional containing the default installation if found.
     */
    Optional<JavaInstallation> findByIsDefaultTrue();
}
