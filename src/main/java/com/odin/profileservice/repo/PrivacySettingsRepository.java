package com.odin.profileservice.repo;

import com.odin.profileservice.entity.PrivacySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for PrivacySettings entity - handles privacy level persistence.
 */
@Repository
public interface PrivacySettingsRepository extends JpaRepository<PrivacySettings, String> {

    /**
     * Find privacy settings by user_id.
     * 
     * @param userId the user's ID
     * @return PrivacySettings if found
     */
    Optional<PrivacySettings> findByUserId(String userId);

    /**
     * Check if privacy settings exist for a user.
     */
    boolean existsByUserId(String userId);
}
