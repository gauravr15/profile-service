package com.odin.profileservice.service;

import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.odin.profileservice.dto.StatusReadinessResponse;
import com.odin.profileservice.entity.*;
import com.odin.profileservice.enums.*;
import com.odin.profileservice.repo.*;
import com.odin.profileservice.utility.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j @Service @RequiredArgsConstructor
public class StatusReadinessService {
    private final ProfileRepository profiles;
    private final UserRepository users;
    private final PrivacySettingsRepository privacy;
    private final SyncAuditRepository syncAudits;
    private final PhoneNumberHasher hasher;
    private final AccountStateValidator accountStateValidator;

    public StatusReadinessResponse assess(String userId) {
        try {
            Profile profile = profiles.findByCustomerId(parse(userId));
            if (profile == null) return indeterminate(StatusReadinessReason.ACCOUNT_NOT_FOUND);
            if (!accountStateValidator.isEligibleForAuth(profile))
                return repair(StatusReadinessReason.ACCOUNT_INACTIVE, null);
            Optional<User> user = users.findById(userId);
            if (user.isEmpty()) return repair(StatusReadinessReason.MIDDLEWARE_USER_MISSING, "REPAIR_SERVER_STATE");
            if (user.get().getGlobalPhoneHash() == null || user.get().getGlobalPhoneHash().isBlank())
                return repair(StatusReadinessReason.GLOBAL_HASH_MISSING, "REPAIR_SERVER_STATE");
            if (user.get().getGlobalPhoneHash().length() < 32)
                return repair(StatusReadinessReason.GLOBAL_HASH_INVALID, "REPAIR_SERVER_STATE");
            if (!Objects.equals(user.get().getPepperVersion(), hasher.getCurrentPepperVersion()))
                return indeterminate(StatusReadinessReason.HASH_VERSION_UNSUPPORTED);
			String normalized = profile.getMobile() == null ? "" : profile.getMobile().replaceAll("[^0-9]", "");
			if (normalized.isEmpty()) return indeterminate(StatusReadinessReason.DATA_INTEGRITY_CONFLICT);
			String authoritativeHash = hasher.hashWithGlobalPepper(normalized);
			if (!authoritativeHash.equals(user.get().getGlobalPhoneHash()))
				return indeterminate(StatusReadinessReason.DATA_INTEGRITY_CONFLICT);
			Optional<User> hashOwner = users.findByGlobalPhoneHash(authoritativeHash);
			if (hashOwner.isPresent() && !userId.equals(hashOwner.get().getUserId()))
				return indeterminate(StatusReadinessReason.DATA_INTEGRITY_CONFLICT);
            if (!privacy.existsByUserId(userId))
                return repair(StatusReadinessReason.PRIVACY_STATE_MISSING, "REPAIR_SERVER_STATE");
            if (!syncAudits.existsById(userId))
                return repair(StatusReadinessReason.CONTACT_SYNC_REQUIRED, "SYNC_CONTACTS");
            return StatusReadinessResponse.builder().state(StatusReadinessState.READY)
                    .reasons(Collections.emptyList()).repairActions(Collections.emptyList()).build();
        } catch (RuntimeException failure) {
            log.warn("Status readiness dependency failure. userId={}, category=DEPENDENCY_FAILURE", userId);
            return indeterminate(StatusReadinessReason.DEPENDENCY_FAILURE);
        }
    }

    @Transactional
    public StatusReadinessResponse repair(String userId) {
        Profile profile = profiles.findByCustomerId(parse(userId));
        if (profile == null) return indeterminate(StatusReadinessReason.ACCOUNT_NOT_FOUND);
        if (!accountStateValidator.isEligibleForAuth(profile)) return repair(StatusReadinessReason.ACCOUNT_INACTIVE, null);
        ensureIdentity(userId, profile);
        if (!privacy.existsByUserId(userId)) {
            try {
                privacy.save(PrivacySettings.builder().userId(userId)
                        .photoPrivacy(PrivacyLevel.MY_CONTACTS).statusPrivacy(PrivacyLevel.MY_CONTACTS)
                        .lastSeenPrivacy(PrivacyLevel.MY_CONTACTS).build());
            } catch (DataIntegrityViolationException concurrent) { /* unique user row won */ }
        }
        return assess(userId);
    }

    private void ensureIdentity(String userId, Profile profile) {
        String normalized = profile.getMobile() == null ? "" : profile.getMobile().replaceAll("[^0-9]", "");
        if (normalized.isEmpty()) throw new IllegalStateException("Authoritative identity unavailable");
        String global = hasher.hashWithGlobalPepper(normalized);
        Optional<User> byId = users.findById(userId);
        if (byId.isPresent()) {
            if (!global.equals(byId.get().getGlobalPhoneHash())) throw new IllegalStateException("Identity conflict");
            return;
        }
        Optional<User> byHash = users.findByGlobalPhoneHash(global);
        if (byHash.isPresent() && !userId.equals(byHash.get().getUserId())) throw new IllegalStateException("Identity conflict");
        String salt = hasher.generateSalt();
        try {
            users.save(User.builder().userId(userId).phoneSalt(salt)
                    .phoneHash(hasher.hashPhoneNumber(normalized, salt)).globalPhoneHash(global)
                    .pepperVersion(hasher.getCurrentPepperVersion()).displayName(profile.getFirstName()).build());
        } catch (DataIntegrityViolationException concurrent) {
            User winner = users.findById(userId).orElseThrow(() -> concurrent);
            if (!global.equals(winner.getGlobalPhoneHash())) throw concurrent;
        }
    }

    private int parse(String id) { try { return Integer.parseInt(id); } catch (Exception e) { throw new IllegalArgumentException(); } }
    private StatusReadinessResponse repair(StatusReadinessReason reason, String action) {
        return StatusReadinessResponse.builder().state(StatusReadinessState.REPAIR_REQUIRED)
                .reasons(Collections.singletonList(reason))
                .repairActions(action == null ? Collections.emptyList() : Collections.singletonList(action)).build();
    }
    private StatusReadinessResponse indeterminate(StatusReadinessReason reason) {
        return StatusReadinessResponse.builder().state(StatusReadinessState.INDETERMINATE)
                .reasons(Collections.singletonList(reason)).repairActions(Collections.emptyList()).retryable(true).build();
    }
}
