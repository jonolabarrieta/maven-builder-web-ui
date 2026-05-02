package net.olaba.mvnbuilder.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import net.olaba.mvnbuilder.entities.FavoriteM2Folder;

/**
 * Repository interface for FavoriteM2Folder entity operations.
 */
@Repository
public interface FavoriteM2FolderRepository extends JpaRepository<FavoriteM2Folder, Long> {
}
