package com.odin.profileservice.service;

import com.odin.profileservice.entity.Contact;
import com.odin.profileservice.entity.User;
import com.odin.profileservice.repo.ContactRepository;
import com.odin.profileservice.repo.UserRepository;
import com.odin.profileservice.utility.PhoneNumberHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Contact service - handles contact operations (save, delete, lookup).
 * All operations use phone hashes, never raw numbers.
 * 
 * Logging uses opaque hash suffixes (last 4 chars) to maintain debuggability
 * without exposing stable identifiers in logs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final PhoneNumberHasher phoneNumberHasher;
    private final PrivacyEvaluationService privacyEvaluationService;

    /**
     * Helper method to extract last 4 characters of hash for opaque logging.
     * Used in log statements to maintain debuggability without exposing full hash.
     */
    private String getOpaqueHashSuffix(String hash) {
        if (hash == null || hash.length() < 4) {
            return hash;
        }
        return hash.substring(hash.length() - 4);
    }

    /**
     * Save a contact by phone number.
     * CRITICAL: Uses GLOBAL hash for deterministic cross-user contact matching.
     * NOTE: contact_name is client-side only, not persisted on server.
     * 
     * @param ownerUserId user saving the contact
     * @param targetPhoneNumber raw phone number being saved
     * @param contactName ignored (for backward compatibility)
     * @param region optional region for normalization
     * @return true if contact was saved/updated
     */
    @Transactional
    public boolean saveContact(String ownerUserId, String targetPhoneNumber, String contactName, String region) {
        try {
            // Normalize and hash target phone number using GLOBAL pepper (deterministic)
            String normalizedPhone = phoneNumberHasher.normalizePhoneNumber(targetPhoneNumber, region);
            String targetGlobalPhoneHash = phoneNumberHasher.hashWithGlobalPepper(normalizedPhone);

            // Check if contact already exists
            if (contactRepository.existsByOwnerUserIdAndTargetGlobalPhoneHash(ownerUserId, targetGlobalPhoneHash)) {
                log.debug("Contact already saved: owner={}", ownerUserId);
                return true;
            }

            // Create and save contact (NO contact_name stored - client-side only)
            Contact contact = Contact.builder()
                    .ownerUserId(ownerUserId)
                    .targetGlobalPhoneHash(targetGlobalPhoneHash)
                    .build();

            contactRepository.save(contact);
            log.info("Contact saved: owner={}, target={}", ownerUserId, getOpaqueHashSuffix(targetGlobalPhoneHash));

            // Invalidate privacy cache
            privacyEvaluationService.invalidateContactCache(ownerUserId, targetGlobalPhoneHash);

            return true;
        } catch (Exception e) {
            log.error("Failed to save contact", e);
            return false;
        }
    }

    /**
     * Save a contact with default region (India).
     */
    @Transactional
    public boolean saveContact(String ownerUserId, String targetPhoneNumber, String contactName) {
        return saveContact(ownerUserId, targetPhoneNumber, contactName, null);
    }

    /**
     * Check if a contact is saved.
     * 
     * @param ownerUserId user who saved the contact
     * @param targetPhoneNumber phone number to check
     * @param region optional region
     * @return true if contact is saved
     */
    public boolean isContactSaved(String ownerUserId, String targetPhoneNumber, String region) {
        try {
            String normalizedPhone = phoneNumberHasher.normalizePhoneNumber(targetPhoneNumber, region);
            String targetGlobalPhoneHash = phoneNumberHasher.hashWithGlobalPepper(normalizedPhone);

            return contactRepository.existsByOwnerUserIdAndTargetGlobalPhoneHash(ownerUserId, targetGlobalPhoneHash);
        } catch (Exception e) {
            log.error("Failed to check if contact is saved", e);
            return false;
        }
    }

    /**
     * Delete a contact.
     * CRITICAL: Uses GLOBAL hash for deterministic cross-user contact matching.
     * 
     * @param ownerUserId user who saved the contact
     * @param targetPhoneNumber phone number to remove
     * @param region optional region
     * @return true if contact was deleted
     */
    @Transactional
    public boolean deleteContact(String ownerUserId, String targetPhoneNumber, String region) {
        try {
            String normalizedPhone = phoneNumberHasher.normalizePhoneNumber(targetPhoneNumber, region);
            String targetGlobalPhoneHash = phoneNumberHasher.hashWithGlobalPepper(normalizedPhone);

            long deleted = contactRepository.deleteByOwnerUserIdAndTargetGlobalPhoneHash(ownerUserId, targetGlobalPhoneHash);
            if (deleted > 0) {
                log.info("Contact deleted: owner={}, target={}", ownerUserId, getOpaqueHashSuffix(targetGlobalPhoneHash));
                privacyEvaluationService.invalidateContactCache(ownerUserId, targetGlobalPhoneHash);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to delete contact", e);
            return false;
        }
    }
}
