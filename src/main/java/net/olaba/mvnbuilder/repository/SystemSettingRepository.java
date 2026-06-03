package net.olaba.mvnbuilder.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import net.olaba.mvnbuilder.entities.SystemSetting;

/**
 * Repository interface for SystemSetting entity operations.
 */
@Repository
public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long> {
}
