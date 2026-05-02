package net.olaba.mvnbuilder.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import net.olaba.mvnbuilder.entities.BuildProfile;

import java.util.Optional;

/**
 * Repository interface for BuildProfile entity operations.
 */
@Repository
public interface BuildProfileRepository extends JpaRepository<BuildProfile, Long> {

    /**
     * Finds the build profile currently set as default.
     * 
     * @return An Optional containing the default profile if found.
     */
    Optional<BuildProfile> findByIsDefaultTrue();
}
