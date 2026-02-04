package com.odin.profileservice.utility;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Phone number utility for normalization and hashing.
 * 
 * TWO DISTINCT HASHING MODES:
 * 
 * 1. Identity Hash (per-user salted):
 *    Purpose: Protect user phone number at rest (account identity)
 *    Formula: SHA-256(normalized_phone + per_user_salt)
 *    Usage: users.phone_hash only, never for contact matching
 * 
 * 2. Global Contact Hash (deterministic pepper-based):
 *    Purpose: Enable cross-user contact matching
 *    Formula: SHA-256(normalized_phone + GLOBAL_PEPPER)
 *    Usage: Contact relationships, blocking, privacy evaluation
 * 
 * CRITICAL: Do NOT use per-user salt for contact matching!
 */
@Slf4j
@Component
public class PhoneNumberHasher {

    private static final Logger logger = LoggerFactory.getLogger(PhoneNumberHasher.class);
    private static final String DEFAULT_REGION = "IN";
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int SALT_LENGTH_BYTES = 32;

    private final PhoneNumberUtil phoneNumberUtil;
    private final SecureRandom secureRandom;

    @Value("${app.security.phone-hash-algorithm:SHA-256}")
    private String hashAlgorithm;

    @Value("${app.security.phone-salt-length:32}")
    private int saltLength;

    @Value("${app.security.global-phone-pepper:}")
    private String globalPhonePepper;

    @Value("${app.security.pepper-version:1}")
    private Integer currentPepperVersion;

    // Backward compatibility: map of old pepper versions for hash verification
    // In production, this would be loaded from configuration
    private static final java.util.Map<Integer, String> PEPPER_HISTORY = new java.util.HashMap<>();

    public PhoneNumberHasher() {
        this.phoneNumberUtil = PhoneNumberUtil.getInstance();
        this.secureRandom = new SecureRandom();
    }

    /**
     * Normalize phone number to E.164 format (e.g., "+919876543210").
     * 
     * @param phoneNumber raw phone number (may or may not have +)
     * @param region optional region code (defaults to IN)
     * @return normalized E.164 format WITHOUT leading + for hashing
     * @throws IllegalArgumentException if number is invalid
     */
    public String normalizePhoneNumber(String phoneNumber, String region) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number cannot be null or empty");
        }

        // 1. Sanitize: remove whitespace, dashes, parentheses but keep '+'
        String sanitized = phoneNumber.replaceAll("[^\\d+]", "");
        
        // 2. Handle '00' prefix as '+' (common in international dialing)
        if (sanitized.startsWith("00")) {
            sanitized = "+" + sanitized.substring(2);
        }

        String regionToUse = region != null && !region.isEmpty() ? region : DEFAULT_REGION;

        try {
            // Attempt A: Standard parse with provided region
            Phonenumber.PhoneNumber number = phoneNumberUtil.parse(sanitized, regionToUse);
            
            if (phoneNumberUtil.isValidNumber(number)) {
                return formatE164(number);
            }

            // Attempt B: If invalid and missing '+', try prepending '+' to check for international format
            if (!sanitized.startsWith("+")) {
                try {
                    Phonenumber.PhoneNumber intlNumber = phoneNumberUtil.parse("+" + sanitized, null);
                    if (phoneNumberUtil.isValidNumber(intlNumber)) {
                        log.debug("Auto-corrected international number by prepending '+': {}", sanitized);
                        return formatE164(intlNumber);
                    }
                } catch (NumberParseException ignored) {
                    // Fall through to original exception
                }
            }

            throw new IllegalArgumentException("Invalid phone number: " + phoneNumber);
        } catch (NumberParseException e) {
            // Last resort: try prepending '+' even if initial parse threw exception
            if (!sanitized.startsWith("+")) {
                try {
                    Phonenumber.PhoneNumber intlNumber = phoneNumberUtil.parse("+" + sanitized, null);
                    if (phoneNumberUtil.isValidNumber(intlNumber)) {
                        return formatE164(intlNumber);
                    }
                } catch (NumberParseException ignored) {}
            }
            throw new IllegalArgumentException("Failed to parse phone number: " + phoneNumber, e);
        }
    }

    /**
     * Helper to format a phone number as E.164 without the '+' prefix.
     */
    private String formatE164(Phonenumber.PhoneNumber number) {
        String e164 = phoneNumberUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
        return e164.startsWith("+") ? e164.substring(1) : e164;
    }

    /**
     * Normalize with default region (India).
     */
    public String normalizePhoneNumber(String phoneNumber) {
        return normalizePhoneNumber(phoneNumber, DEFAULT_REGION);
    }

    /**
     * Generate a cryptographically secure random salt.
     * 
     * @return Base64-encoded salt
     */
    public String generateSalt() {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        secureRandom.nextBytes(salt);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(salt);
    }

    /**
     * Hash a normalized phone number with salt using SHA-256.
     * 
     * @param normalizedPhoneNumber phone number without leading + (post-normalization)
     * @param salt Base64-encoded salt
     * @return Base64-encoded hash
     */
    public String hashPhoneNumber(String normalizedPhoneNumber, String salt) {
        if (normalizedPhoneNumber == null || normalizedPhoneNumber.isEmpty()) {
            throw new IllegalArgumentException("Normalized phone number cannot be null or empty");
        }
        if (salt == null || salt.isEmpty()) {
            throw new IllegalArgumentException("Salt cannot be null or empty");
        }

        try {
            // Combine: normalizedPhone + salt
            String input = normalizedPhoneNumber + salt;
            byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);

            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(inputBytes);

            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Hash a normalized phone number with GLOBAL_PEPPER (deterministic, cross-user).
     * Used for contact matching, blocking, and privacy evaluation.
     * Same phone → same hash across all users.
     * 
     * @param normalizedPhoneNumber phone number without leading + (post-normalization)
     * @return Base64-encoded hash
     */
    public String hashWithGlobalPepper(String normalizedPhoneNumber) {
        if (normalizedPhoneNumber == null || normalizedPhoneNumber.isEmpty()) {
            throw new IllegalArgumentException("Normalized phone number cannot be null or empty");
        }
        if (globalPhonePepper == null || globalPhonePepper.isEmpty()) {
            throw new IllegalArgumentException("GLOBAL_PEPPER not configured. Set app.security.global-phone-pepper in config");
        }

        try {
            // Combine: normalizedPhone + GLOBAL_PEPPER
            String input = normalizedPhoneNumber + globalPhonePepper;
            byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);

            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(inputBytes);

            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Complete flow: normalize → hash with user salt (identity hash).
     * Used during user registration.
     * 
     * @param phoneNumber raw phone number
     * @param salt salt for this user
     * @param region optional region
     * @return hashed phone number (identity)
     */
    public String normalizeAndHash(String phoneNumber, String salt, String region) {
        String normalized = normalizePhoneNumber(phoneNumber, region);
        return hashPhoneNumber(normalized, salt);
    }

    /**
     * Complete flow with default region (identity hash).
     */
    public String normalizeAndHash(String phoneNumber, String salt) {
        return normalizeAndHash(phoneNumber, salt, DEFAULT_REGION);
    }

    /**
     * Complete flow: normalize → hash with global pepper (global contact hash).
     * Used for contact matching, blocking, privacy evaluation.
     * 
     * @param phoneNumber raw phone number
     * @param region optional region
     * @return hashed phone number (deterministic global)
     */
    public String normalizeAndHashWithPepper(String phoneNumber, String region) {
        String normalized = normalizePhoneNumber(phoneNumber, region);
        return hashWithGlobalPepper(normalized);
    }

    /**
     * Complete flow with default region (global contact hash).
     */
    public String normalizeAndHashWithPepper(String phoneNumber) {
        return normalizeAndHashWithPepper(phoneNumber, DEFAULT_REGION);
    }

    /**
     * Get current pepper version.
     * Used when creating new hashes to track which pepper was used.
     * 
     * @return current pepper version
     */
    public Integer getCurrentPepperVersion() {
        return currentPepperVersion != null ? currentPepperVersion : 1;
    }

    /**
     * Hash with specific pepper version (for backward compatibility).
     * 
     * @param normalizedPhoneNumber phone number without leading +
     * @param pepperVersion version to use for hashing
     * @return Base64-encoded hash using specified pepper version
     */
    public String hashWithGlobalPepperVersion(String normalizedPhoneNumber, Integer pepperVersion) {
        if (normalizedPhoneNumber == null || normalizedPhoneNumber.isEmpty()) {
            throw new IllegalArgumentException("Normalized phone number cannot be null or empty");
        }

        String pepperToUse = getPepperForVersion(pepperVersion);
        if (pepperToUse == null || pepperToUse.isEmpty()) {
            throw new IllegalArgumentException("No pepper configured for version: " + pepperVersion);
        }

        try {
            String input = normalizedPhoneNumber + pepperToUse;
            byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);

            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(inputBytes);

            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Get pepper string for given version.
     * Version 1 uses current globalPhonePepper.
     * Older versions would be retrieved from PEPPER_HISTORY.
     * 
     * @param version pepper version
     * @return pepper string for this version
     */
    private String getPepperForVersion(Integer version) {
        if (version == null || version <= 0) {
            return null;
        }

        // Current version
        if (version.equals(getCurrentPepperVersion())) {
            return globalPhonePepper;
        }

        // Historical version (would be managed in PEPPER_HISTORY)
        return PEPPER_HISTORY.getOrDefault(version, null);
    }

    /**
     * Register a historical pepper version for backward compatibility.
     * Called during initialization or configuration updates.
     * 
     * @param version pepper version
     * @param pepper pepper value
     */
    public synchronized void registerPepperVersion(Integer version, String pepper) {
        PEPPER_HISTORY.put(version, pepper);
        logger.info("Registered pepper version: {}", version);
    }
}

