package com.odin.profileservice.service;

import com.odin.profileservice.entity.PrivacySettings;
import com.odin.profileservice.entity.Profile;
import com.odin.profileservice.entity.User;
import com.odin.profileservice.enums.PrivacyLevel;
import com.odin.profileservice.repo.PrivacySettingsRepository;
import com.odin.profileservice.repo.ProfileRepository;
import com.odin.profileservice.repo.UserRepository;
import com.odin.profileservice.utility.PhoneNumberHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Privacy settings service - handles CRUD for user privacy levels.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrivacySettingsService {

    private final PrivacySettingsRepository privacySettingsRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final PhoneNumberHasher phoneNumberHasher;
    private final ContactTokenService contactTokenService;
    private final PrivacyEvaluationService privacyEvaluationService;
    private final PrivacyFcmPublisher privacyFcmPublisher;

    /**
     * Get or create privacy settings for a user.
     * Creates with defaults if not found.
     * If user doesn't exist in middleware, tries to sync from Core first.
     */
    public PrivacySettings getOrCreateSettings(String userId) {
        Optional<PrivacySettings> existing = privacySettingsRepository.findByUserId(userId);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Check if user exists in middleware
        if (!userRepository.existsById(userId)) {
            // Try to sync user from Core (like ContactSyncController does)
            try {
                ensureUserInMiddleware(userId);
                log.info("Synced user {} to middleware", userId);
            } catch (Exception e) {
                log.warn("Failed to sync user {} from Core: {}", userId, e.getMessage());
                // Return null to signal user creation failed - let caller handle
                return null;
            }
        }

        // Default to EVERYONE for first-time users
        PrivacySettings newSettings = PrivacySettings.builder()
            .userId(userId)
            .photoPrivacy(PrivacyLevel.EVERYONE)
            .statusPrivacy(PrivacyLevel.EVERYONE)
            .lastSeenPrivacy(PrivacyLevel.EVERYONE)
            .build();

        PrivacySettings saved = privacySettingsRepository.save(newSettings);
        log.info("Created default privacy settings for user: {}", userId);
        return saved;
    }

    /**
     * Ensure user exists in middleware by fetching from Core and creating if necessary.
     * Uses same pattern as ContactSyncController.
     */
    @Transactional
    private void ensureUserInMiddleware(String userId) {
        try {
            // Convert userId (customerId) to Integer for Core query
            Integer customerId = Integer.parseInt(userId);
            
            // Query Core to get user profile
            Profile userProfile = profileRepository.findByCustomerId(customerId);
            if (userProfile == null) {
                throw new IllegalArgumentException("User not found in Core");
            }
            
            // Check if user already exists in middleware
            String normalizedUserPhone = normalizePhoneSimple(userProfile.getMobile());
            String globalPhoneHash = phoneNumberHasher.hashWithGlobalPepper(normalizedUserPhone);
                ContactTokenService.LookupTokenMaterial tokenMaterial =
                    contactTokenService.deriveLookupTokensFromCanonical(normalizedUserPhone);
            Optional<User> middlewareUser = userRepository.findByGlobalPhoneHash(globalPhoneHash);
            
            if (!middlewareUser.isPresent()) {
                // Create user in middleware (same pattern as ContactSyncController)
                String phoneSalt = phoneNumberHasher.generateSalt();
                String phoneHash = phoneNumberHasher.hashPhoneNumber(normalizedUserPhone, phoneSalt);
                
                User newUser = User.builder()
                        .userId(userId)
                        .phoneHash(phoneHash)
                    .phoneToken(tokenMaterial.getCurrentToken())
                    .phoneTokenVersion(tokenMaterial.getCurrentVersion())
                        .phoneSalt(phoneSalt)
                        .globalPhoneHash(globalPhoneHash)
                    .globalPhoneToken(tokenMaterial.getCurrentToken())
                    .globalPhoneTokenVersion(tokenMaterial.getCurrentVersion())
                        .pepperVersion(1)
                        .displayName((userProfile.getFirstName() != null ? userProfile.getFirstName() : "") + 
                                   " " + (userProfile.getLastName() != null ? userProfile.getLastName() : ""))
                        .build();
                
                userRepository.save(newUser);
                log.info("Created middleware user for customerId: {}", customerId);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid userId format", e);
        }
    }

    /**
     * Normalize phone to digits only (no + prefix, no formatting).
     */
    private String normalizePhoneSimple(String phone) {
        if (phone == null || phone.isEmpty()) {
            return "";
        }
        return phone.replaceAll("[^0-9]", "");
    }

    /**
     * Update privacy settings for a user.
     * 
     * @param userId the user
     * @param photoPrivacy privacy level for photos
     * @param statusPrivacy privacy level for status
     * @param lastSeenPrivacy privacy level for last seen
     * @return updated settings
     */
    @Transactional
    public PrivacySettings updateSettings(String userId, PrivacyLevel photoPrivacy, 
                                         PrivacyLevel statusPrivacy, PrivacyLevel lastSeenPrivacy) {
        PrivacySettings settings = getOrCreateSettings(userId);

        // Store old privacy levels for FCM publishing
        PrivacyLevel oldPhotoPrivacy = settings.getPhotoPrivacy();
        PrivacyLevel oldLastSeenPrivacy = settings.getLastSeenPrivacy();
        PrivacyLevel oldStatusPrivacy = settings.getStatusPrivacy();

        boolean changed = false;
        StringBuilder changeLog = new StringBuilder();
        
        if (photoPrivacy != null && !photoPrivacy.equals(settings.getPhotoPrivacy())) {
            changeLog.append(String.format("photo: %s→%s ", settings.getPhotoPrivacy(), photoPrivacy));
            settings.setPhotoPrivacy(photoPrivacy);
            changed = true;
        }
        if (statusPrivacy != null && !statusPrivacy.equals(settings.getStatusPrivacy())) {
            changeLog.append(String.format("status: %s→%s ", settings.getStatusPrivacy(), statusPrivacy));
            settings.setStatusPrivacy(statusPrivacy);
            changed = true;
        }
        if (lastSeenPrivacy != null && !lastSeenPrivacy.equals(settings.getLastSeenPrivacy())) {
            changeLog.append(String.format("lastSeen: %s→%s", settings.getLastSeenPrivacy(), lastSeenPrivacy));
            settings.setLastSeenPrivacy(lastSeenPrivacy);
            changed = true;
        }

        if (changed) {
            PrivacySettings updated = privacySettingsRepository.save(settings);
            log.info("[PRIVACY-UPDATE] ✅ Privacy settings updated for user: {} | Changes: {}", userId, changeLog.toString());
            
            // Invalidate privacy cache so next checks use updated settings
            privacyEvaluationService.invalidatePrivacySettingsCache(userId);
            
            // Publish FCM notifications to affected contacts (async via Kafka)
            log.info("[PRIVACY-UPDATE] 📤 Triggering FCM publishing for userId={}", userId);
            privacyFcmPublisher.publishPrivacyChange(
                    userId,
                    photoPrivacy != null ? photoPrivacy : oldPhotoPrivacy,
                    lastSeenPrivacy != null ? lastSeenPrivacy : oldLastSeenPrivacy,
                    oldPhotoPrivacy,
                    oldLastSeenPrivacy);
            
            return updated;
        }

        return settings;
    }

    /**
     * Reset to defaults.
     */
    @Transactional
    public PrivacySettings resetToDefaults(String userId) {
        return updateSettings(userId, PrivacyLevel.EVERYONE, PrivacyLevel.EVERYONE, PrivacyLevel.EVERYONE);
    }
}
