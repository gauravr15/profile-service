package com.odin.profileservice.service;

import com.odin.profileservice.dto.PrivacyVisibilityChangeEvent;
import com.odin.profileservice.entity.Contact;
import com.odin.profileservice.entity.User;
import com.odin.profileservice.enums.PrivacyLevel;
import com.odin.profileservice.repo.ContactRepository;
import com.odin.profileservice.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service to publish FCM notifications when privacy settings change.
 * Determines affected contacts and sends PRIVACY_CHANGE notifications via Kafka.
 * 
 * Similar to StatusVisibilityService but for privacy visibility changes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrivacyFcmPublisher {

    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final KafkaTemplate<String, PrivacyVisibilityChangeEvent> kafkaTemplate;

    private static final String KAFKA_TOPIC = "privacy-visibility-updates";

    /**
     * Publish privacy change notifications to affected contacts.
     * Called after privacy settings update.
     * 
     * @param userId the user who changed privacy settings
     * @param newPhotoPrivacy new photo privacy level
     * @param newLastSeenPrivacy new last seen privacy level
     * @param oldPhotoPrivacy old photo privacy level
     * @param oldLastSeenPrivacy old last seen privacy level
     */
    @Transactional(readOnly = true)
    public void publishPrivacyChange(
            String userId,
            PrivacyLevel newPhotoPrivacy,
            PrivacyLevel newLastSeenPrivacy,
            PrivacyLevel oldPhotoPrivacy,
            PrivacyLevel oldLastSeenPrivacy) {

        log.info("[PRIVACY-FCM] 🔒 Publishing privacy change for userId={}. Old: photo={}, lastSeen={}. New: photo={}, lastSeen={}",
                userId, oldPhotoPrivacy, oldLastSeenPrivacy, newPhotoPrivacy, newLastSeenPrivacy);

        try {
            // Get user's global phone hash for contact lookups
            User user = userRepository.findById(userId)
                    .orElse(null);
            if (user == null) {
                log.warn("[PRIVACY-FCM] ⚠️ User not found: {}", userId);
                return;
            }

            String userGlobalHash = user.getGlobalPhoneHash();

            // Find all potentially affected contacts
            Set<String> affectedContacts = new HashSet<>();

            // For PHOTO privacy changes
            if (!newPhotoPrivacy.equals(oldPhotoPrivacy)) {
                log.info("[PRIVACY-FCM] 📸 Photo privacy changed from {} to {}. Finding affected contacts...",
                        oldPhotoPrivacy, newPhotoPrivacy);
                
                Set<String> photoAffected = findAffectedContactsForAttribute(
                        userId, userGlobalHash, oldPhotoPrivacy, newPhotoPrivacy);
                affectedContacts.addAll(photoAffected);
                log.info("[PRIVACY-FCM] 📸 Found {} contacts affected by photo privacy change", photoAffected.size());
            }

            // For LAST_SEEN privacy changes
            if (!newLastSeenPrivacy.equals(oldLastSeenPrivacy)) {
                log.info("[PRIVACY-FCM] 👁️ Last seen privacy changed from {} to {}. Finding affected contacts...",
                        oldLastSeenPrivacy, newLastSeenPrivacy);
                
                Set<String> lastSeenAffected = findAffectedContactsForAttribute(
                        userId, userGlobalHash, oldLastSeenPrivacy, newLastSeenPrivacy);
                affectedContacts.addAll(lastSeenAffected);
                log.info("[PRIVACY-FCM] 👁️ Found {} contacts affected by last seen privacy change", lastSeenAffected.size());
            }

            log.info("[PRIVACY-FCM] 📤 Total affected contacts: {}. Publishing to Kafka topic: {}",
                    affectedContacts.size(), KAFKA_TOPIC);

            // Publish GRANTED event (new privacy allows visibility)
            if (isPrivacyGranting(newPhotoPrivacy) || isPrivacyGranting(newLastSeenPrivacy)) {
                publishEvent(userId, affectedContacts, "GRANTED", newPhotoPrivacy, newLastSeenPrivacy);
            }

            // Publish REVOKED event (new privacy restricts visibility)
            if (isPrivacyRevoking(oldPhotoPrivacy, newPhotoPrivacy) || 
                isPrivacyRevoking(oldLastSeenPrivacy, newLastSeenPrivacy)) {
                publishEvent(userId, affectedContacts, "REVOKED", oldPhotoPrivacy, oldLastSeenPrivacy);
            }

            log.info("[PRIVACY-FCM] ✅ Privacy change published successfully for userId={}", userId);

        } catch (Exception e) {
            log.error("[PRIVACY-FCM] ❌ Error publishing privacy change for userId={}", userId, e);
        }
    }

    /**
     * Find contacts affected by privacy change for a specific attribute.
     * This includes:
     * - Contacts saved by the user (affected by MY_CONTACTS privacy)
     * - Contacts who saved the user (affected by EVERYONE or MY_CONTACTS privacy)
     */
    private Set<String> findAffectedContactsForAttribute(
            String userId,
            String userGlobalHash,
            PrivacyLevel oldPrivacy,
            PrivacyLevel newPrivacy) {

        Set<String> affected = new HashSet<>();

        // Case 1: NOBODY → EVERYONE or MY_CONTACTS (GRANT visibility)
        if (oldPrivacy == PrivacyLevel.NOBODY && 
            (newPrivacy == PrivacyLevel.EVERYONE || newPrivacy == PrivacyLevel.MY_CONTACTS)) {
            
            // Everyone who saved this user can now see
            List<Contact> savingContacts = contactRepository.findByTargetGlobalPhoneHash(userGlobalHash);
            for (Contact contact : savingContacts) {
                affected.add(contact.getOwnerUserId());
            }
            log.debug("[PRIVACY-FCM] 📍 NOBODY→{}: {} contacts who saved this user now have visibility",
                    newPrivacy, affected.size());
        }

        // Case 2: EVERYONE → NOBODY (REVOKE from everyone)
        else if (oldPrivacy == PrivacyLevel.EVERYONE && newPrivacy == PrivacyLevel.NOBODY) {
            // Everyone who was seeing this needs to be notified
            // = all contacts saved by user + all contacts who saved user
            
            List<Contact> savedContacts = contactRepository.findByOwnerUserId(userId);
            for (Contact contact : savedContacts) {
                User contactUser = userRepository.findByGlobalPhoneHash(contact.getTargetGlobalPhoneHash())
                        .orElse(null);
                if (contactUser != null) {
                    affected.add(contactUser.getUserId());
                }
            }

            List<Contact> savingContacts = contactRepository.findByTargetGlobalPhoneHash(userGlobalHash);
            for (Contact contact : savingContacts) {
                affected.add(contact.getOwnerUserId());
            }
            log.debug("[PRIVACY-FCM] 📍 EVERYONE→NOBODY: {} total contacts will be notified",
                    affected.size());
        }

        // Case 3: EVERYONE → MY_CONTACTS (REVOKE from non-contacts)
        else if (oldPrivacy == PrivacyLevel.EVERYONE && newPrivacy == PrivacyLevel.MY_CONTACTS) {
            // Find contacts who were seeing but are not saved contacts
            List<Contact> savingContacts = contactRepository.findByTargetGlobalPhoneHash(userGlobalHash);
            List<Contact> savedContacts = contactRepository.findByOwnerUserId(userId);
            
            Set<String> savedContactGlobalHashes = new HashSet<>();
            for (Contact contact : savedContacts) {
                savedContactGlobalHashes.add(contact.getTargetGlobalPhoneHash());
            }

            for (Contact savingContact : savingContacts) {
                if (!savedContactGlobalHashes.contains(savingContact.getOwnerUserId())) {
                    affected.add(savingContact.getOwnerUserId());
                }
            }
            log.debug("[PRIVACY-FCM] 📍 EVERYONE→MY_CONTACTS: {} non-contacts losing visibility",
                    affected.size());
        }

        // Case 4: MY_CONTACTS → NOBODY (REVOKE from all contacts)
        else if (oldPrivacy == PrivacyLevel.MY_CONTACTS && newPrivacy == PrivacyLevel.NOBODY) {
            List<Contact> savedContacts = contactRepository.findByOwnerUserId(userId);
            for (Contact contact : savedContacts) {
                User contactUser = userRepository.findByGlobalPhoneHash(contact.getTargetGlobalPhoneHash())
                        .orElse(null);
                if (contactUser != null) {
                    affected.add(contactUser.getUserId());
                }
            }
            log.debug("[PRIVACY-FCM] 📍 MY_CONTACTS→NOBODY: {} contacts losing visibility",
                    affected.size());
        }

        // Case 5: MY_CONTACTS → EVERYONE (GRANT to non-contacts)
        else if (oldPrivacy == PrivacyLevel.MY_CONTACTS && newPrivacy == PrivacyLevel.EVERYONE) {
            List<Contact> savingContacts = contactRepository.findByTargetGlobalPhoneHash(userGlobalHash);
            List<Contact> savedContacts = contactRepository.findByOwnerUserId(userId);
            
            Set<String> savedContactGlobalHashes = new HashSet<>();
            for (Contact contact : savedContacts) {
                savedContactGlobalHashes.add(contact.getTargetGlobalPhoneHash());
            }

            for (Contact savingContact : savingContacts) {
                if (!savedContactGlobalHashes.contains(savingContact.getOwnerUserId())) {
                    affected.add(savingContact.getOwnerUserId());
                }
            }
            log.debug("[PRIVACY-FCM] 📍 MY_CONTACTS→EVERYONE: {} non-contacts gaining visibility",
                    affected.size());
        }

        return affected;
    }

    /**
     * Publish event to Kafka for notification service to consume.
     */
    private void publishEvent(
            String userId,
            Set<String> affectedContacts,
            String action,
            PrivacyLevel photoPrivacy,
            PrivacyLevel lastSeenPrivacy) {

        if (affectedContacts.isEmpty()) {
            log.debug("[PRIVACY-FCM] ⚪ No affected contacts for action={}", action);
            return;
        }

        try {
            PrivacyVisibilityChangeEvent event = PrivacyVisibilityChangeEvent.builder()
                    .userId(userId)
                    .photoPrivacy(photoPrivacy.name())
                    .lastSeenPrivacy(lastSeenPrivacy.name())
                    .eligibleContactIds(new ArrayList<>(affectedContacts))
                    .timestamp(System.currentTimeMillis())
                    .build();

            // Use userId as message key to ensure ordering
            kafkaTemplate.send(KAFKA_TOPIC, userId, event);

            log.info("[PRIVACY-FCM] 📨 Published {} event for userid={} to {} contacts. Topic={}",
                    action, userId, affectedContacts.size(), KAFKA_TOPIC);
        } catch (Exception e) {
            log.error("[PRIVACY-FCM] ❌ Failed to publish event to Kafka for userId={}", userId, e);
        }
    }

    private boolean isPrivacyGranting(PrivacyLevel privacy) {
        return privacy == PrivacyLevel.EVERYONE || privacy == PrivacyLevel.MY_CONTACTS;
    }

    private boolean isPrivacyRevoking(PrivacyLevel oldPrivacy, PrivacyLevel newPrivacy) {
        // Privacy is revoking if going from less restrictive to more restrictive
        return oldPrivacy != PrivacyLevel.NOBODY && 
               (newPrivacy == PrivacyLevel.NOBODY || 
                (oldPrivacy == PrivacyLevel.EVERYONE && newPrivacy == PrivacyLevel.MY_CONTACTS));
    }
}
