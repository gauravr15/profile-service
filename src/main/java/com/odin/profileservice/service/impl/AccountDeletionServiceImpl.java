package com.odin.profileservice.service.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import com.odin.profileservice.constants.LanguageConstants;
import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.AccountDeletionEvent;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.entity.Group;
import com.odin.profileservice.entity.Profile;
import com.odin.profileservice.entity.User;
import com.odin.profileservice.repo.BlockedContactRepository;
import com.odin.profileservice.repo.ContactExceptionRepository;
import com.odin.profileservice.repo.ContactRepository;
import com.odin.profileservice.repo.GroupRepository;
import com.odin.profileservice.repo.PrivacySettingsRepository;
import com.odin.profileservice.repo.ProfileRepository;
import com.odin.profileservice.repo.RefreshTokenRepository;
import com.odin.profileservice.repo.SyncAuditRepository;
import com.odin.profileservice.repo.UserRepository;
import com.odin.profileservice.service.AccountDeletionService;
import com.odin.profileservice.utility.AccountDeletionProducer;
import com.odin.profileservice.utility.ResponseObject;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AccountDeletionServiceImpl implements AccountDeletionService {

    @Autowired
    private ProfileRepository profileRepo;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepo;

    @Autowired
    private PrivacySettingsRepository privacySettingsRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private BlockedContactRepository blockedContactRepository;

    @Autowired
    private ContactExceptionRepository contactExceptionRepository;

    @Autowired
    private SyncAuditRepository syncAuditRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private AccountDeletionProducer accountDeletionProducer;

    @Autowired
    private ResponseObject response;

    @Override
    @Transactional
    public ResponseDTO deleteAccount(String customerId) {
        log.info("[DELETE-ACCOUNT] Starting account deletion for customerId={}", customerId);

        // --- Validate input ---
        if (ObjectUtils.isEmpty(customerId)) {
            return response.buildResponse(LanguageConstants.EN, ResponseCodes.INVALID_REQUEST);
        }

        Integer customerIdInt;
        try {
            customerIdInt = Integer.parseInt(customerId);
        } catch (NumberFormatException e) {
            log.error("[DELETE-ACCOUNT] Invalid customerId format: {}", customerId);
            return response.buildResponse(LanguageConstants.EN, ResponseCodes.INVALID_REQUEST);
        }

        // --- Step 1: Load and validate profile from core service (REST) ---
        Profile profile = profileRepo.findByCustomerId(customerIdInt);
        if (ObjectUtils.isEmpty(profile)) {
            log.warn("[DELETE-ACCOUNT] Profile not found for customerId={}", customerId);
            return response.buildResponse(LanguageConstants.EN, ResponseCodes.USER_NOT_EXISTS);
        }

        if (Boolean.TRUE.equals(profile.getIsDeleted())) {
            log.warn("[DELETE-ACCOUNT] Account already deleted for customerId={}", customerId);
            return response.buildResponse(LanguageConstants.EN, ResponseCodes.USER_NOT_EXISTS);
        }

        // --- Step 2: Capture globalPhoneHash BEFORE deletion (needed for Kafka event) ---
        String globalPhoneHash = null;
        Optional<User> middlewareUserOpt = userRepository.findById(customerId);
        if (middlewareUserOpt.isPresent()) {
            globalPhoneHash = middlewareUserOpt.get().getGlobalPhoneHash();
            log.info("[DELETE-ACCOUNT] Captured globalPhoneHash for customerId={}", customerId);
        } else {
            log.warn("[DELETE-ACCOUNT] No mw_users entry found for customerId={}, proceeding with deletion", customerId);
        }

        // --- Step 3: Mark profile and auth as deleted in core service (via REST) ---
        profile.setIsDeleted(true);
        profile.setIsActive(false);
        if (!ObjectUtils.isEmpty(profile.getAuth())) {
            profile.getAuth().setIsDeleted(true);
            profile.getAuth().setIsActive(false);
        }
        profileRepo.update(profile);
        log.info("[DELETE-ACCOUNT] Core service profile and auth marked as deleted for customerId={}", customerId);

        // --- Step 4: Clean up profile-service owned tables (JPA) ---

        // 4a. Privacy settings (1:1 with user)
        privacySettingsRepository.findByUserId(customerId)
                .ifPresent(ps -> {
                    privacySettingsRepository.delete(ps);
                    log.info("[DELETE-ACCOUNT] privacy_settings deleted for userId={}", customerId);
                });

        // 4b. Contacts owned by this user (all contacts this user had saved)
        List<com.odin.profileservice.entity.Contact> ownedContacts = contactRepository.findByOwnerUserId(customerId);
        if (!ownedContacts.isEmpty()) {
            contactRepository.deleteAll(ownedContacts);
            log.info("[DELETE-ACCOUNT] {} contact(s) deleted for ownerUserId={}", ownedContacts.size(), customerId);
        }

        // 4c. Blocked contacts created by this user
        List<com.odin.profileservice.entity.BlockedContact> blockedContacts =
                blockedContactRepository.findByBlockerUserId(customerId);
        if (!blockedContacts.isEmpty()) {
            blockedContactRepository.deleteAll(blockedContacts);
            log.info("[DELETE-ACCOUNT] {} blocked_contact(s) deleted for blockerUserId={}", blockedContacts.size(), customerId);
        }

        // 4d. Contact exceptions owned by this user
        List<com.odin.profileservice.entity.ContactException> contactExceptions =
                contactExceptionRepository.findByOwnerUserId(customerId);
        if (!contactExceptions.isEmpty()) {
            contactExceptionRepository.deleteAll(contactExceptions);
            log.info("[DELETE-ACCOUNT] {} contact_exception(s) deleted for ownerUserId={}", contactExceptions.size(), customerId);
        }

        // 4e. Sync audit entry
        syncAuditRepository.findById(customerId)
                .ifPresent(sa -> {
                    syncAuditRepository.delete(sa);
                    log.info("[DELETE-ACCOUNT] mw_sync_audit deleted for userId={}", customerId);
                });

        // 4f. mw_users row — clears profile photo URL, display name, all hashes
        //     (profile photo will appear fresh on re-registration)
        middlewareUserOpt.ifPresent(user -> {
            userRepository.delete(user);
            log.info("[DELETE-ACCOUNT] mw_users entry deleted for userId={}", customerId);
        });

        // --- Step 5: Revoke all active sessions (refresh_tokens) ---
        long deletedTokens = refreshTokenRepo.deleteByCustomerId(Long.valueOf(customerIdInt));
        log.info("[DELETE-ACCOUNT] {} refresh_token(s) revoked for customerId={}", deletedTokens, customerId);

        // --- Step 6: Remove user from all group memberships and admin roles ---
        List<Group> memberGroups = groupRepository.findByMemberId(customerId);
        if (!memberGroups.isEmpty()) {
            for (Group group : memberGroups) {
                group.getMembers().remove(customerId);
                group.getAdmins().remove(customerId);
            }
            groupRepository.saveAll(memberGroups);
            log.info("[DELETE-ACCOUNT] Removed customerId={} from {} group(s)", customerId, memberGroups.size());
        }

        // --- Step 7: Publish Kafka event for downstream contact-sync cleanup ---
        AccountDeletionEvent event = AccountDeletionEvent.builder()
                .customerId(customerId)
                .globalPhoneHash(globalPhoneHash)
                .timestamp(System.currentTimeMillis())
                .build();
        accountDeletionProducer.publish(event);
        log.info("[DELETE-ACCOUNT] Account deletion completed successfully for customerId={}", customerId);

        return response.buildResponse(LanguageConstants.EN, ResponseCodes.SUCCESS_CODE);
    }
}
