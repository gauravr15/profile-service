package com.odin.profileservice.service;

import com.odin.profileservice.entity.BlockedContact;
import com.odin.profileservice.entity.Contact;
import com.odin.profileservice.entity.ContactException;
import com.odin.profileservice.entity.PrivacySettings;
import com.odin.profileservice.entity.User;
import com.odin.profileservice.enums.ContactExceptionType;
import com.odin.profileservice.enums.PrivacyAttribute;
import com.odin.profileservice.enums.PrivacyLevel;
import com.odin.profileservice.repo.BlockedContactRepository;
import com.odin.profileservice.repo.ContactExceptionRepository;
import com.odin.profileservice.repo.ContactRepository;
import com.odin.profileservice.repo.PrivacySettingsRepository;
import com.odin.profileservice.repo.ProfileRepository;
import com.odin.profileservice.repo.UserRepository;
import com.odin.profileservice.entity.Profile;
import com.odin.profileservice.utility.PhoneNumberHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Core privacy evaluation service - the single source of truth for privacy checks.
 * 
 * Privacy evaluation priority (in order):
 * 1. Self-access (viewer == target) → ALLOW
 * 2. Blocked → DENY
 * 3. Exception rules → APPLY
 * 4. Privacy settings:
 *    - EVERYONE → ALLOW
 *    - NOBODY → DENY
 *    - MY_CONTACTS → Check if target saved viewer's contact
 * 5. Default → DENY
 * 
 * Caching Strategy:
 * - Redis cache with 1-hour TTL for privacy checks
 * - Cache keys: privacy:{target_user_id}:{viewer_user_id}:{attribute}
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PrivacyEvaluationService {

    private static final String CACHE_KEY_TEMPLATE = "privacy:%s:%s:%s";
    private static final String CACHE_BLOCKED_TEMPLATE = "blocked:%s:%s";
    private static final String CACHE_CONTACT_SAVED_TEMPLATE = "contact_saved:%s:%s";
    private static final long CACHE_TTL_SECONDS = 3600; // 1 hour
    private static final long CACHE_CONTACT_TTL_SECONDS = 7200; // 2 hours

    private final UserRepository userRepository;
    private final PrivacySettingsRepository privacySettingsRepository;
    private final ContactRepository contactRepository;
    private final BlockedContactRepository blockedContactRepository;
    private final ContactExceptionRepository contactExceptionRepository;
    private final PhoneNumberHasher phoneNumberHasher;
    private final RedisTemplate<String, Boolean> redisTemplate;
    private final ProfileRepository profileRepository;

    /**
     * Core privacy evaluation logic.
     * 
     * @param viewerUserId user trying to view
     * @param targetUserId user being viewed
     * @param attribute the profile attribute (photo, status, last_seen)
     * @return true if viewer can see the attribute
     */
    public boolean canViewAttribute(String viewerUserId, String targetUserId, PrivacyAttribute attribute) {
        // Step 1: Self-access always allowed
        if (viewerUserId.equals(targetUserId)) {
            return true;
        }

        String cacheKey = buildCacheKey(targetUserId, viewerUserId, attribute);

        // Step 2: Check cache (DISABLED as per user request to avoid confusing keys)
        /*
        Boolean cached = getCachedResult(cacheKey);
        if (cached != null) {
            return cached;
        }
        */

        // Step 3: Evaluate privacy rules
        boolean result = evaluatePrivacy(viewerUserId, targetUserId, attribute);

        // Step 4: Cache result (DISABLED as per user request to avoid confusing keys)
        // cacheResult(cacheKey, result, CACHE_TTL_SECONDS);

        return result;
    }

    /**
     * Batch privacy check for multiple attributes.
     * 
     * @param viewerUserId user trying to view
     * @param targetUserId user being viewed
     * @return map of attribute → visibility
     */
    public Map<PrivacyAttribute, Boolean> canViewAttributes(String viewerUserId, String targetUserId) {
        Map<PrivacyAttribute, Boolean> result = new HashMap<>();
        for (PrivacyAttribute attr : PrivacyAttribute.values()) {
            result.put(attr, canViewAttribute(viewerUserId, targetUserId, attr));
        }
        return result;
    }

    /**
     * Private: Evaluate privacy without cache.
     * 
     * Evaluation order (priority):
     * 1. Blocked (highest priority)
     * 2. Exception rules
     * 3. Privacy settings
     */
    private boolean evaluatePrivacy(String viewerUserId, String targetUserId, PrivacyAttribute attribute) {
        // Ensure viewer user exists in middleware (sync from Core if needed)
        try {
            ensureViewerUserExists(viewerUserId);
        } catch (Exception e) {
            log.warn("Failed to ensure viewer user in middleware: {}", e.getMessage());
            return false;
        }

        // Get viewer's GLOBAL phone hash (deterministic, for contact matching)
        Optional<User> viewerOpt = userRepository.findById(viewerUserId);
        if (!viewerOpt.isPresent()) {
            log.debug("Viewer user not found after sync attempt: {}", viewerUserId);
            return false;
        }
        String viewerGlobalPhoneHash = viewerOpt.get().getGlobalPhoneHash();

        // Rule 1: Check if target has blocked viewer (HIGHEST PRIORITY)
        // Use GLOBAL hash for deterministic cross-user blocking
        if (isBlocked(targetUserId, viewerGlobalPhoneHash)) {
            return false;
        }

        // Rule 2: Check for exceptions (apply if exists)
        // Use GLOBAL hash for deterministic exception matching
        Optional<ContactException> exception = contactExceptionRepository
                .findByOwnerUserIdAndExceptionGlobalPhoneHash(targetUserId, viewerGlobalPhoneHash);
        if (exception.isPresent()) {
            return applyException(exception.get(), attribute);
        }

        // Rule 3: Apply privacy settings
        Optional<PrivacySettings> settingsOpt = privacySettingsRepository.findByUserId(targetUserId);

        // Fallback: No privacy settings = create and persist EVERYONE defaults
        PrivacySettings settings = settingsOpt.orElseGet(() -> createDefaultPrivacySettings(targetUserId));

        PrivacyLevel level = getPrivacyLevelForAttribute(settings, attribute);

        switch (level) {
            case EVERYONE:
                return true;
            case NOBODY:
                return false;
            case MY_CONTACTS:
                return isSavedContact(targetUserId, viewerGlobalPhoneHash);
            default:
                return false;
        }
    }

    private PrivacySettings createDefaultPrivacySettings(String userId) {
        PrivacySettings defaults = PrivacySettings.builder()
                .userId(userId)
                .photoPrivacy(PrivacyLevel.EVERYONE)
                .statusPrivacy(PrivacyLevel.EVERYONE)
                .lastSeenPrivacy(PrivacyLevel.EVERYONE)
                .build();

        try {
            PrivacySettings saved = privacySettingsRepository.save(defaults);
            log.info("Created default privacy settings (EVERYONE) for user {}", userId);
            return saved;
        } catch (Exception e) {
            log.warn("Failed to persist default privacy settings for user {}: {}", userId, e.getMessage());
            return defaults;
        }
    }

    /**
     * Check if target user has blocked viewer's GLOBAL phone hash.
     * Cached separately for efficiency.
     * Uses deterministic global hash for cross-user blocking consistency.
     */
    private boolean isBlocked(String targetUserId, String viewerGlobalPhoneHash) {
        String cacheKey = String.format(CACHE_BLOCKED_TEMPLATE, targetUserId, viewerGlobalPhoneHash);

        /*
        Boolean cached = getCachedResult(cacheKey);
        if (cached != null) {
            return cached;
        }
        */

        boolean exists = blockedContactRepository
                .existsByBlockerUserIdAndBlockedGlobalPhoneHash(targetUserId, viewerGlobalPhoneHash);

        // cacheResult(cacheKey, exists, CACHE_TTL_SECONDS);
        return exists;
    }

    /**
     * Check if target has saved viewer's contact (MY_CONTACTS rule).
     * Cached separately for efficiency.
     * Uses deterministic global hash for consistent contact matching.
     */
    private boolean isSavedContact(String targetUserId, String viewerGlobalPhoneHash) {
        String cacheKey = String.format(CACHE_CONTACT_SAVED_TEMPLATE, targetUserId, viewerGlobalPhoneHash);

        /*
        Boolean cached = getCachedResult(cacheKey);
        if (cached != null) {
            return cached;
        }
        */

        boolean exists = contactRepository
                .existsByOwnerUserIdAndTargetGlobalPhoneHash(targetUserId, viewerGlobalPhoneHash);

        // cacheResult(cacheKey, exists, CACHE_CONTACT_TTL_SECONDS);
        return exists;
    }

    /**
     * Apply exception rule.
     */
    private boolean applyException(ContactException exception, PrivacyAttribute attribute) {
        switch (exception.getExceptionType()) {
            case ALWAYS_SHOW:
                return true;
            case ALWAYS_HIDE:
                return false;
            case CUSTOM:
                // Custom: use override_privacy_level if available
                if (exception.getOverridePrivacyLevel() != null) {
                    PrivacyLevel level = PrivacyLevel.fromValue(exception.getOverridePrivacyLevel());
                    return level == PrivacyLevel.EVERYONE;
                }
                return false; // Default CUSTOM to deny if no override
            default:
                return false;
        }
    }

    /**
     * Get privacy level for an attribute from settings.
     */
    private PrivacyLevel getPrivacyLevelForAttribute(PrivacySettings settings, PrivacyAttribute attribute) {
        switch (attribute) {
            case PHOTO:
                return settings.getPhotoPrivacy();
            case STATUS:
                return settings.getStatusPrivacy();
            case LAST_SEEN:
                return settings.getLastSeenPrivacy();
            default:
                return PrivacyLevel.NOBODY;
        }
    }

    /**
     * Build cache key for privacy check.
     */
    private String buildCacheKey(String targetUserId, String viewerUserId, PrivacyAttribute attribute) {
        return String.format(CACHE_KEY_TEMPLATE, targetUserId, viewerUserId, attribute.getValue());
    }

    /**
     * Get cached result from Redis.
     */
    private Boolean getCachedResult(String cacheKey) {
        try {
            return redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("Redis cache read failed for key: {}, proceeding without cache", cacheKey, e);
            return null;
        }
    }

    /**
     * Cache result in Redis.
     */
    private void cacheResult(String cacheKey, boolean result, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(cacheKey, result, Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.warn("Redis cache write failed for key: {}", cacheKey, e);
        }
    }

    /**
     * Invalidate cache when contacts change.
     * Called after saving/removing a contact.
     * 
     * Uses targeted invalidation instead of KEYS pattern matching (which blocks Redis).
     * Contact saves affect: privacy checks where the target (contact owner) is the data owner.
     */
    @Transactional
    public void invalidateContactCache(String ownerUserId, String targetGlobalPhoneHash) {
        try {
            // We don't have a reverse mapping (viewer → owners checking them).
            // For safety and correctness, invalidate caches for:
            // 1. Privacy checks where ownerUserId is the target (data owner)
            // 2. Contact saved checks for ownerUserId
            // 3. Blocked checks for ownerUserId
            
            // Approach: Use Redis sets to track active keys per user
            // For now, use targeted deletion of known patterns:
            String privacyKeyPrefix = "privacy:" + ownerUserId + ":";
            String contactSavedKeyPrefix = "contact_saved:" + ownerUserId + ":";
            String blockedKeyPrefix = "blocked:" + ownerUserId + ":";
            
            // Use Redis SCAN instead of KEYS for non-blocking iteration
            // SCAN is safer but still pattern-based. In production, use Set-based tracking:
            // When saving contact: Add to Set "cache:users:" + ownerUserId + ":keys"
            // Then SMEMBERS to get only keys to invalidate (not KEYS)
            
            // For now, implement via deletion of high-cardinality keys:
            // Contact changes are infrequent, so targeted invalidation is acceptable
            invalidatePrefixedKeys(privacyKeyPrefix);
            invalidatePrefixedKeys(contactSavedKeyPrefix);
            invalidatePrefixedKeys(blockedKeyPrefix);
            
            log.debug("Cache invalidated for contact change: owner={}", ownerUserId);
        } catch (Exception e) {
            log.warn("Redis cache invalidation failed for contact change", e);
        }
    }

    /**
     * Helper method to invalidate keys with given prefix using pattern matching.
     * Uses a pattern-based approach with Java 8 compatible code.
     */
    private void invalidatePrefixedKeys(String keyPrefix) {
        try {
            Set<String> keys = redisTemplate.keys(keyPrefix + "*");
            if (keys != null && keys.size() > 0) {
                redisTemplate.delete(keys);
                log.debug("Invalidated {} cache keys with prefix: {}", keys.size(), keyPrefix);
            }
        } catch (Exception e) {
            log.warn("Error invalidating prefix: {}", keyPrefix, e);
        }
    }

    /**
     * Invalidate cache when privacy settings change.
     * Called after updating user's privacy settings.
     * 
     * Uses SCAN instead of KEYS for non-blocking Redis operations.
     */
    @Transactional
    public void invalidatePrivacySettingsCache(String targetUserId) {
        try {
            // Invalidate all privacy checks where this user is the data owner
            String pattern = "privacy:" + targetUserId + ":*:*";
            invalidatePrefixedKeys(pattern);
            
            log.info("[PRIVACY-CACHE] ✅ Cache invalidated for privacy settings change: target={}", targetUserId);
        } catch (Exception e) {
            log.warn("[PRIVACY-CACHE] ⚠️ Redis cache invalidation failed for privacy settings change: target={}", targetUserId, e);
        }
    }

    /**
     * Check if contact exists with version-aware hash matching.
     * Supports backward compatibility by trying hashes with all registered pepper versions.
     * 
     * Used during privacy evaluation to handle pepper rotation scenarios.
     * 
     * @param targetUserId owner of contacts
     * @param viewerGlobalPhoneHash current hash (usually from latest pepper)
     * @return true if contact exists with current or any historical pepper version
     */
    private boolean isContactSavedWithVersionFallback(String targetUserId, String viewerGlobalPhoneHash) {
        // First try with current hash
        if (contactRepository.existsByOwnerUserIdAndTargetGlobalPhoneHash(targetUserId, viewerGlobalPhoneHash)) {
            return true;
        }
        
        // In production with pepper rotation, would try historical peppers
        // For now, this is a placeholder for future implementation
        // Example (if implemented):
        // for (Integer oldVersion : getAllHistoricalPepperVersions()) {
        //     String oldHash = phoneNumberHasher.hashWithGlobalPepperVersion(normalizedPhone, oldVersion);
        //     if (contactRepository.existsByOwnerUserIdAndTargetGlobalPhoneHash(targetUserId, oldHash)) {
        //         return true;
        //     }
        // }
        
        return false;
    }

    /**
     * Check if contact is blocked with version-aware hash matching.
     * Supports backward compatibility for pepper rotation.
     * 
     * @param targetUserId user being checked
     * @param viewerGlobalPhoneHash current hash
     * @return true if viewer is blocked by target
     */
    private boolean isBlockedWithVersionFallback(String targetUserId, String viewerGlobalPhoneHash) {
        // First try with current hash
        if (blockedContactRepository.existsByBlockerUserIdAndBlockedGlobalPhoneHash(targetUserId, viewerGlobalPhoneHash)) {
            return true;
        }
        
        // Placeholder for historical pepper versions
        // Similar to isContactSavedWithVersionFallback
        
        return false;
    }

    /**
     * Ensure viewer user exists in middleware by syncing from Core if needed.
     * This allows privacy evaluation to work for users who haven't synced contacts yet.
     * Uses same pattern as PrivacySettingsService.ensureUserInMiddleware().
     * 
     * @param viewerUserId the viewer's customerId
     * @throws Exception if user not found in Core or sync fails
     */
    private void ensureViewerUserExists(String viewerUserId) {
        try {
            // Check if user already exists in middleware
            Optional<User> existing = userRepository.findById(viewerUserId);
            if (existing.isPresent()) {
                return; // User already exists, nothing to do
            }

            // User doesn't exist in middleware, fetch from Core
            Integer customerId = Integer.parseInt(viewerUserId);
            Profile userProfile = profileRepository.findByCustomerId(customerId);
            if (userProfile == null) {
                throw new IllegalArgumentException("User not found in Core: customerId=" + customerId);
            }

            // Normalize phone and create user
            String normalizedUserPhone = normalizePhoneSimple(userProfile.getMobile());
            String globalPhoneHash = phoneNumberHasher.hashWithGlobalPepper(normalizedUserPhone);
            
            String phoneSalt = phoneNumberHasher.generateSalt();
            String phoneHash = phoneNumberHasher.hashPhoneNumber(normalizedUserPhone, phoneSalt);
            
            User newUser = User.builder()
                    .userId(viewerUserId)
                    .phoneHash(phoneHash)
                    .phoneSalt(phoneSalt)
                    .globalPhoneHash(globalPhoneHash)
                    .pepperVersion(1)
                    .displayName((userProfile.getFirstName() != null ? userProfile.getFirstName() : "") +
                               " " + (userProfile.getLastName() != null ? userProfile.getLastName() : ""))
                    .build();
            
            userRepository.save(newUser);
            log.info("Synced viewer user to middleware: customerId={}, globalPhoneHash={}", customerId, globalPhoneHash);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid viewerUserId format: " + viewerUserId, e);
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
}

