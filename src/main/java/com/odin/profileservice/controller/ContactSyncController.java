package com.odin.profileservice.controller;

import com.odin.profileservice.dto.ContactSyncRequest;
import com.odin.profileservice.dto.ContactSyncResponse;
import com.odin.profileservice.dto.MobileListDTO;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.entity.Profile;
import com.odin.profileservice.entity.User;
import com.odin.profileservice.repo.ProfileRepository;
import com.odin.profileservice.repo.UserRepository;
import com.odin.profileservice.service.ContactService;
import com.odin.profileservice.service.PrivacySettingsService;
import com.odin.profileservice.service.SyncAuditService;
import com.odin.profileservice.utility.PhoneNumberHasher;
import com.odin.profileservice.utility.ResponseObject;
import com.odin.profileservice.constants.ResponseCodes;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Contact sync endpoint.
 * Receives raw phone numbers (over TLS only), hashes them, and stores contact relationships.
 * 
 * POST /api/v1/contacts/sync
 */
@Slf4j
@RestController
@RequestMapping("/v1/contacts")
@RequiredArgsConstructor
public class ContactSyncController {
	
	@Autowired
	private SyncAuditService sync;
	
	@Value("update.sync.time")
	private String updateSyncTime;

    private static final int MAX_CONTACTS_PER_SYNC = 5000;

    private final ContactService contactService;
    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final PrivacySettingsService privacySettingsService;
    private final PhoneNumberHasher phoneNumberHasher;
    private final ResponseObject responseObject;

    /**
     * Sync contacts from mobile device.
     * 
     * Raw phone numbers are accepted here (TLS only), hashed server-side,
     * and only contact relationships are stored (no profile data).
     * 
     * @param request contact list with phone numbers
     * @param customerId customer ID from gateway header
     * @return sync result with newly discovered users
     */
    @PostMapping("/sync")
    @Transactional
    public ResponseEntity<ResponseDTO> syncContacts(
            @RequestBody ContactSyncRequest request,
            @RequestHeader(value = "customerId", required = true) String customerId) {

        if (customerId == null || customerId.trim().isEmpty()) {
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.INVALID_REQUEST);
            return ResponseEntity.ok(response);
        }

        String userId = customerId;

        try {
            // Validate request
            if (request.getContacts() == null || request.getContacts().isEmpty()) {
                ResponseDTO response = responseObject.buildResponse(ResponseCodes.INVALID_REQUEST);
                return ResponseEntity.ok(response);
            }

            if (request.getContacts().size() > MAX_CONTACTS_PER_SYNC) {
                ResponseDTO response = responseObject.buildResponse(ResponseCodes.INVALID_REQUEST);
                return ResponseEntity.ok(response);
            }

            // Fetch user from Core to validate customerId exists
            Profile userProfile = null;
            String middlewareUserId = null;
            try {
                Integer customIdInt = Integer.parseInt(customerId);
                userProfile = profileRepository.findByCustomerId(customIdInt);
                if (userProfile == null) {
                    ResponseDTO response = responseObject.buildResponse(ResponseCodes.NO_DATA_FOUND);
                    return ResponseEntity.ok(response);
                }
                
                // Find or create corresponding user in middleware
                String normalizedUserPhone = normalizePhoneSimple(userProfile.getMobile());
                String globalPhoneHash = phoneNumberHasher.hashWithGlobalPepper(normalizedUserPhone);
                
                // First, try to find user by userId (customerId) - this is the primary key
                java.util.Optional<User> middlewareUserByUserId = userRepository.findById(customerId);
                
                if (middlewareUserByUserId.isPresent()) {
                    middlewareUserId = middlewareUserByUserId.get().getUserId();
                    log.info("Found existing middleware user by userId: {}", middlewareUserId);
                } else {
                    // User doesn't exist by userId, check if it exists by globalPhoneHash
                    java.util.Optional<User> middlewareUserByHash = userRepository.findByGlobalPhoneHash(globalPhoneHash);
                    
                    if (middlewareUserByHash.isPresent()) {
                        // User exists by hash but with different userId (shouldn't happen in normal flow)
                        middlewareUserId = middlewareUserByHash.get().getUserId();
                        log.warn("Found user by globalPhoneHash but different userId. Existing: {}, Expected: {}", middlewareUserId, customerId);
                    } else {
                        // Create user in middleware if doesn't exist
                        String phoneSalt = phoneNumberHasher.generateSalt();
                        String phoneHash = phoneNumberHasher.hashPhoneNumber(normalizedUserPhone, phoneSalt);
                        
                        log.info("Building new user with userId: {}", customerId);
                        User newUser = User.builder()
                            .userId(customerId)
                            .phoneHash(phoneHash)
                            .phoneSalt(phoneSalt)
                            .globalPhoneHash(globalPhoneHash)
                            .pepperVersion(1)
                            .displayName((userProfile.getFirstName() != null ? userProfile.getFirstName() : "") + 
                                       " " + (userProfile.getLastName() != null ? userProfile.getLastName() : ""))
                            .build();
                        
                        log.info("User object before save - userId: {}, globalPhoneHash: {}", newUser.getUserId(), newUser.getGlobalPhoneHash());
                        log.info("Creating new middleware user with userId: {}, globalPhoneHash: {}", customerId, globalPhoneHash);
                        userRepository.save(newUser);
                        log.info("User object after save - userId: {}", newUser.getUserId());
                        middlewareUserId = newUser.getUserId();
                        log.info("Created middleware user for customerId: {} with userId: {}", customerId, middlewareUserId);
                    }
                }
                
            } catch (NumberFormatException e) {
                ResponseDTO response = responseObject.buildResponse(ResponseCodes.INVALID_REQUEST);
                return ResponseEntity.ok(response);
            } catch (Exception e) {
                log.error("Failed to validate user in Core", e);
                ResponseDTO response = responseObject.buildResponse(ResponseCodes.INTERNAL_SERVER_ERROR);
                return ResponseEntity.ok(response);
            }
            
            // Verify middlewareUserId was set
            if (middlewareUserId == null) {
                log.error("middlewareUserId is null after user validation/creation");
                ResponseDTO response = responseObject.buildResponse(ResponseCodes.INTERNAL_SERVER_ERROR);
                return ResponseEntity.ok(response);
            }
            
            log.info("User synced successfully with middlewareUserId: {}", middlewareUserId);

            // Ensure default privacy settings exist (EVERYONE for first-time users)
            try {
                privacySettingsService.getOrCreateSettings(middlewareUserId);
            } catch (Exception ex) {
                log.warn("Failed to ensure privacy settings for user {}: {}", middlewareUserId, ex.getMessage());
            }

            int syncedCount = 0;
            List<ContactSyncResponse.NewUserInfo> newUsers = new ArrayList<>();
            
            // Normalize and collect all phone numbers for batch query to Core (using libphonenumber like fetchCustomerByMobile)
            PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
            Set<String> normalizedPhones = new HashSet<>();
            Map<String, String> rawToNormalizedMap = new HashMap<>();
            
            // Extract region from request country code OR user's phone
            String requestRegion = null;
            if (request.getCountryCode() != null && !request.getCountryCode().trim().isEmpty()) {
                try {
                    String countryCode = request.getCountryCode().replaceAll("[^0-9]", "");
                    if (!countryCode.isEmpty()) {
                        int code = Integer.parseInt(countryCode);
                        List<String> regions = phoneNumberUtil.getRegionCodesForCountryCode(code);
                        if (!regions.isEmpty()) {
                            requestRegion = regions.get(0);
                            log.debug("Using region from request country code: {} -> {}", countryCode, requestRegion);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not extract region from request country code: {}", request.getCountryCode());
                }
            }
            
            // Fallback: Extract region from user's phone (if available)
            String userRegion = null;
            if (userProfile != null && userProfile.getMobile() != null) {
                try {
                    Phonenumber.PhoneNumber userPhoneNumber = phoneNumberUtil.parse(
                        "+" + normalizePhoneSimple(userProfile.getMobile()), null
                    );
                    userRegion = phoneNumberUtil.getRegionCodeForNumber(userPhoneNumber);
                    log.debug("Extracted region from user phone: {}", userRegion);
                } catch (Exception e) {
                    log.debug("Could not extract region from user phone");
                }
            }
            
            // Use request region if available, otherwise user region
            String normalizeRegion = requestRegion != null ? requestRegion : userRegion;
            
            for (ContactSyncRequest.ContactItem contact : request.getContacts()) {
                if (contact == null || contact.getPhoneNumber() == null || contact.getPhoneNumber().trim().isEmpty()) {
                    log.debug("Skipping empty contact phone number");
                    continue;
                }
                
                try {
                    String normalizedPhone = normalizePhoneNumber(
                        contact.getPhoneNumber(), 
                        normalizeRegion
                    );
                    if (normalizedPhone != null) {
                        normalizedPhones.add(normalizedPhone);
                        rawToNormalizedMap.put(normalizedPhone, contact.getPhoneNumber());
                    }
                } catch (Exception e) {
                    log.debug("Failed to normalize contact phone: {}", contact.getPhoneNumber());
                }
            }
            
            // Batch query Core to get registered customers (bulk sync pattern)
            List<Profile> registeredProfiles = new ArrayList<>();
            if (!normalizedPhones.isEmpty()) {
                try {
                    // Call Core microservice via ProfileRepository using bulk API
                    registeredProfiles = profileRepository.findLikeMobileNumber(new ArrayList<>(normalizedPhones), true);
                } catch (Exception e) {
                    log.warn("Failed to fetch registered users from Core", e);
                    // Continue processing - Core might be temporarily unavailable
                }
            }
            
            // Create set of registered phone numbers (normalized format from Core)
            // Also map profiles so we can create users
            Set<String> registeredPhones = new HashSet<>();
            Map<String, Profile> phoneToProfileMap = new HashMap<>();
            for (Profile profile : registeredProfiles) {
                if (profile.getMobile() != null) {
                    String normalizedPhone = normalizePhoneSimple(profile.getMobile());
                    registeredPhones.add(normalizedPhone);
                    phoneToProfileMap.put(normalizedPhone, profile);
                }
            }

            // Process each contact
            for (ContactSyncRequest.ContactItem contact : request.getContacts()) {
                if (contact == null || contact.getPhoneNumber() == null || contact.getPhoneNumber().trim().isEmpty()) {
                    continue;
                }
                
                try {
                    String normalizedPhone = normalizePhoneNumber(
                        contact.getPhoneNumber(), 
                        userRegion
                    );
                    
                    if (normalizedPhone == null) {
                        continue;
                    }
                    
                    // Save contact (normalizes and hashes phone number with GLOBAL pepper)
                    if (contactService.saveContact(middlewareUserId, contact.getPhoneNumber(), "", userRegion)) {
                        syncedCount++;

                        // Check if this phone number is registered in Core
                        boolean isRegistered = registeredPhones.contains(normalizedPhone);
                        
                        if (isRegistered) {
                            // Create/sync corresponding user in middleware for this contact
                            Profile contactProfile = phoneToProfileMap.get(normalizedPhone);
                            if (contactProfile != null) {
                                try {
                                    // Ensure contact user exists in middleware
                                    String contactCustomerId = String.valueOf(contactProfile.getCustomerId());
                                    java.util.Optional<User> existingContactUser = userRepository.findById(contactCustomerId);
                                    
                                    if (!existingContactUser.isPresent()) {
                                        // Create user for this contact
                                        String contactPhoneSalt = phoneNumberHasher.generateSalt();
                                        String contactPhoneHash = phoneNumberHasher.hashPhoneNumber(normalizedPhone, contactPhoneSalt);
                                        String contactGlobalPhoneHash = phoneNumberHasher.hashWithGlobalPepper(normalizedPhone);
                                        
                                        User contactUser = User.builder()
                                            .userId(contactCustomerId)
                                            .phoneHash(contactPhoneHash)
                                            .phoneSalt(contactPhoneSalt)
                                            .globalPhoneHash(contactGlobalPhoneHash)
                                            .pepperVersion(1)
                                            .displayName((contactProfile.getFirstName() != null ? contactProfile.getFirstName() : "") +
                                                       " " + (contactProfile.getLastName() != null ? contactProfile.getLastName() : ""))
                                            .build();
                                        
                                        userRepository.save(contactUser);
                                        log.info("Created middleware user for contact: customerId={}, userId={}", contactCustomerId, contactCustomerId);
                                    }
                                } catch (Exception e) {
                                    log.warn("Failed to sync contact user to middleware: {}", e.getMessage());
                                }
                            }
                            
                            // Return opaque identifier (last 4 chars of global hash) for UX purposes
                            String targetGlobalHash = phoneNumberHasher.hashWithGlobalPepper(normalizedPhone);
                            String phoneHashSuffix = targetGlobalHash.length() > 4 
                                ? targetGlobalHash.substring(targetGlobalHash.length() - 4) 
                                : targetGlobalHash;
                            
                            newUsers.add(ContactSyncResponse.NewUserInfo.builder()
                                    .isRegistered(true)
                                    .phoneHashSuffix(phoneHashSuffix)
                                    .build());
                        }
                    }
                } catch (Exception e) {
                    // Log error but continue with other contacts (never log raw phone)
                    log.debug("Failed to sync contact", e);
                }
            }

            // Build response
            ContactSyncResponse contactSyncResponse = ContactSyncResponse.builder()
                    .syncedCount(syncedCount)
                    .newUsers(newUsers)
                    .nextSyncToken(generateSyncToken())
                    .syncTimestamp(System.currentTimeMillis())
                    .build();
            
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.SUCCESS_CODE, contactSyncResponse);
            
            if (Boolean.valueOf(updateSyncTime)) {
    			updateSyncDetails(customerId);
    		}
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Contact sync failed", e);
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.INTERNAL_SERVER_ERROR);
            return ResponseEntity.ok(response);
        }
    }
    
    void updateSyncDetails(String customerId) {
		sync.updateLastSyncedTime(customerId);
	}

    /**
     * Generate next sync token for pagination (future use).
     */
    private String generateSyncToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * Normalize phone number using libphonenumber (EXACT same logic as fetchCustomerByMobile in LoginServiceImpl).
     * 
     * Returns normalized phone WITHOUT country code prefix (e.g., "919876543210" not "+919876543210")
     * This matches how Core stores phone numbers during registration/login.
     * 
     * Handles multiple formats: +91-XXXX, XXXXXXXXXX, with/without country code
     * Global support using libphonenumber library.
     */
    private String normalizePhoneNumber(String rawPhone, String region) {
        if (rawPhone == null || rawPhone.trim().isEmpty()) {
            return null;
        }

        try {
            PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
            String cleaned = rawPhone.replaceAll("[^0-9+]", "");
            if (cleaned.isEmpty()) {
                return null;
            }

            Phonenumber.PhoneNumber phoneNumber = null;

            // CASE 1: starts with + → parse directly
            if (cleaned.startsWith("+")) {
                phoneNumber = phoneNumberUtil.parse(cleaned, null);
            } else {
                // CASE 2: Try using region
                try {
                    phoneNumber = phoneNumberUtil.parse(cleaned, region);

                    // If invalid, fallback to international
                    if (!phoneNumberUtil.isValidNumber(phoneNumber)) {
                        phoneNumber = phoneNumberUtil.parse("+" + cleaned, null);
                    }
                } catch (Exception e) {
                    // Fallback for numbers like 66629..., 9199..., etc.
                    phoneNumber = phoneNumberUtil.parse("+" + cleaned, null);
                }
            }

            // Final validation
            if (phoneNumberUtil.isValidNumber(phoneNumber)) {
                String normalized = phoneNumberUtil
                        .format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164);

                // CRITICAL: Remove + prefix to match Core storage format
                // Core stores: 919876543210 (NOT +919876543210)
                if (normalized.startsWith("+")) {
                    normalized = normalized.substring(1);
                }
                return normalized;
            }

        } catch (Exception e) {
            log.debug("Failed to normalize phone number: {}", rawPhone);
        }

        return null;
    }

    /**
     * Simple phone normalization - remove all non-digits.
     * Used for matching with Core profile data (which stores as digits only: 919876543210).
     */
    private String normalizePhoneSimple(String phone) {
        if (phone == null) {
            return null;
        }
        return phone.replaceAll("[^0-9]", "");
    }
}
