package com.odin.profileservice.service;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.odin.profileservice.config.ContactDiscoveryProperties;
import com.odin.profileservice.constants.LanguageConstants;
import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.CustomerDetailsDTO;
import com.odin.profileservice.dto.MobileListDTO;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.entity.BlockedContact;
import com.odin.profileservice.entity.Profile;
import com.odin.profileservice.entity.User;
import com.odin.profileservice.repo.BlockedContactRepository;
import com.odin.profileservice.repo.ProfileRepository;
import com.odin.profileservice.repo.UserRepository;
import com.odin.profileservice.utility.PhoneNumberHasher;
import com.odin.profileservice.utility.ResponseObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactDiscoveryService {
    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final BlockedContactRepository blockedContactRepository;
    private final PhoneNumberHasher phoneNumberHasher;
    private final ContactDiscoveryRateLimiter rateLimiter;
    private final ContactDiscoveryProperties properties;
    private final ResponseObject response;
    private final SyncAuditService syncAuditService;

    @Value("${update.sync.time:false}")
    private boolean updateSyncTime;

    public ResponseDTO discover(String customerId, MobileListDTO request) {
        long startedAt = System.nanoTime();
        if (customerId == null || !customerId.matches("[1-9][0-9]{0,18}")) {
            throw ContactDiscoveryException.invalidRequest();
        }
        ValidatedRequest validated = validate(request);
        if (validated.canonicalToSubmitted.size() > properties.getTargetBatchSize()) {
            log.warn("Contact discovery request above target batch size inputCount={}",
                    validated.canonicalToSubmitted.size());
        }
        rateLimiter.enforce(customerId, new ArrayList<>(validated.canonicalToSubmitted.keySet()));

        List<Profile> profiles;
        try {
            profiles = profileRepository.findLikeMobileNumber(
                    new ArrayList<>(validated.canonicalToSubmitted.keySet()), true);
        } catch (RuntimeException ex) {
            log.warn("Contact discovery dependency unavailable");
            throw ContactDiscoveryException.unavailable();
        }
        if (profiles == null) {
            throw ContactDiscoveryException.unavailable();
        }

        List<Profile> validProfiles = profiles.stream()
                .filter(this::isConsistentActiveProfile)
                .filter(profile -> validated.canonicalToSubmitted.containsKey(
                        digitsOnly(profile.getMobile())))
                .collect(Collectors.toList());
        Set<Integer> duplicates = validProfiles.stream()
                .collect(Collectors.groupingBy(Profile::getCustomerId, Collectors.counting()))
                .entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        validProfiles.removeIf(profile -> duplicates.contains(profile.getCustomerId()));

        List<Profile> eligible = filterBlocked(customerId, validProfiles);
        Map<String, CustomerDetailsDTO> result = new LinkedHashMap<>();
        for (Profile profile : eligible) {
            String canonical = digitsOnly(profile.getMobile());
            String submitted = validated.canonicalToSubmitted.get(canonical);
            result.put(submitted, CustomerDetailsDTO.builder()
                    .customerId(profile.getCustomerId())
                    .firstName(profile.getFirstName())
                    .lastName(profile.getLastName())
                    .mobile(profile.getMobile())
                    .build());
        }

        int matched = result.size();
        int unmatched = validated.canonicalToSubmitted.size() - matched;
        rateLimiter.recordOutcome(customerId, matched, unmatched);
        log.info("Contact discovery completed inputCount={} matchedCount={} filteredCount={} durationMs={}",
                validated.canonicalToSubmitted.size(), matched,
                Math.max(0, profiles.size() - eligible.size()),
                (System.nanoTime() - startedAt) / 1_000_000L);

        if (result.isEmpty()) {
            return response.buildResponse(LanguageConstants.EN, ResponseCodes.FAILURE_CODE);
        }
        if (updateSyncTime) {
            syncAuditService.updateLastSyncedTime(customerId);
        }
        return response.buildResponse(LanguageConstants.EN, ResponseCodes.SUCCESS_CODE, result);
    }

    private ValidatedRequest validate(MobileListDTO request) {
        if (request == null || request.getMobile() == null) {
            logInvalidRequest("NULL_LIST", uniqueInputCount(request));
            throw ContactDiscoveryException.invalidRequest();
        }
        if (request.getMobile().isEmpty()) {
            logInvalidRequest("OTHER_VALIDATION_REASON", uniqueInputCount(request));
            throw ContactDiscoveryException.invalidRequest();
        }
        if (request.getMobile().size() > properties.getMaxNumbers()) {
            logInvalidRequest("TOO_MANY_NUMBERS", uniqueInputCount(request));
            throw ContactDiscoveryException.invalidRequest();
        }
        String region = resolveRegion(request.getCountryCode());
        Map<String, String> canonicalToSubmitted = new LinkedHashMap<>();
        for (String value : request.getMobile()) {
            if (value == null || value.trim().isEmpty()) {
                logInvalidRequest("EMPTY_OR_NULL_ITEM", uniqueInputCount(request));
                throw ContactDiscoveryException.invalidRequest();
            }
            if (value.length() > properties.getMaxPhoneLength()) {
                logInvalidRequest("PHONE_TOO_LONG", uniqueInputCount(request));
                throw ContactDiscoveryException.invalidRequest();
            }
            if (!value.matches("[0-9+().\\-\\s]+")) {
                logInvalidRequest("INVALID_PHONE_FORMAT", uniqueInputCount(request));
                throw ContactDiscoveryException.invalidRequest();
            }
            final String canonical;
            try {
                canonical = phoneNumberHasher.normalizePhoneNumber(value, region);
            } catch (IllegalArgumentException ex) {
                logInvalidRequest("OTHER_VALIDATION_REASON", uniqueInputCount(request));
                throw ContactDiscoveryException.invalidRequest();
            }
            canonicalToSubmitted.putIfAbsent(canonical, value);
        }
        return new ValidatedRequest(canonicalToSubmitted);
    }

    private String resolveRegion(String countryCode) {
        if (countryCode == null) {
            logInvalidRequest("INVALID_COUNTRY_CODE", -1);
            throw ContactDiscoveryException.invalidRequest();
        }
        String trimmed = countryCode.trim();
        if (!trimmed.matches("\\+?[0-9]{1,3}")) {
            logInvalidRequest("INVALID_COUNTRY_CODE", -1);
            throw ContactDiscoveryException.invalidRequest();
        }
        int callingCode;
        try {
            callingCode = Integer.parseInt(trimmed.replace("+", ""));
        } catch (NumberFormatException ex) {
            logInvalidRequest("INVALID_COUNTRY_CODE", -1);
            throw ContactDiscoveryException.invalidRequest();
        }
        List<String> regions = PhoneNumberUtil.getInstance()
                .getRegionCodesForCountryCode(callingCode);
        if (regions.isEmpty()) {
            logInvalidRequest("INVALID_COUNTRY_CODE", -1);
            throw ContactDiscoveryException.invalidRequest();
        }
        return regions.get(0);
    }

    private void logInvalidRequest(String validationReason, int uniqueInputCount) {
        log.warn(
                "[BULK-CUSTOMER-DETAILS][VALIDATION] traceId={} uniqueInputCount={} validationReason={}",
                traceId(),
                uniqueInputCount,
                validationReason);
    }

    private int uniqueInputCount(MobileListDTO request) {
        if (request == null || request.getMobile() == null) {
            return -1;
        }
        return (int) request.getMobile().stream()
                .filter(value -> value != null)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .count();
    }

    private String traceId() {
        String traceId = MDC.get("correlationId");
        if (traceId == null || traceId.isBlank()) {
            traceId = MDC.get("traceId");
        }
        return traceId == null || traceId.isBlank() ? "unknown" : traceId;
    }

    private boolean isConsistentActiveProfile(Profile profile) {
        return profile != null
                && profile.getCustomerId() != null
                && Boolean.TRUE.equals(profile.getIsActive())
                && !Boolean.TRUE.equals(profile.getIsDeleted())
                && profile.getMobile() != null
                && !digitsOnly(profile.getMobile()).isEmpty();
    }

    private List<Profile> filterBlocked(String requesterId, List<Profile> profiles) {
        if (profiles.isEmpty()) {
            return profiles;
        }
        String requesterHash = requesterHash(requesterId);
        Map<String, Profile> byHash = profiles.stream().collect(Collectors.toMap(
                profile -> protectedPhone(profile.getMobile()),
                Function.identity(),
                (first, ignored) -> first,
                LinkedHashMap::new));
        Set<String> targetIds = profiles.stream()
                .map(profile -> String.valueOf(profile.getCustomerId()))
                .collect(Collectors.toSet());
        Set<String> blockedByRequester = blockedContactRepository
                .findByBlockerUserIdAndBlockedGlobalPhoneHashIn(
                        requesterId, byHash.keySet())
                .stream()
                .map(BlockedContact::getBlockedGlobalPhoneHash)
                .collect(Collectors.toSet());
        Set<String> requesterBlockedByTargets = blockedContactRepository
                .findByBlockerUserIdInAndBlockedGlobalPhoneHash(
                        targetIds, requesterHash)
                .stream()
                .map(BlockedContact::getBlockerUserId)
                .collect(Collectors.toSet());
        return byHash.entrySet().stream()
                .filter(entry -> !blockedByRequester.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(profile -> !requesterBlockedByTargets.contains(
                        String.valueOf(profile.getCustomerId())))
                .collect(Collectors.toList());
    }

    private String requesterHash(String requesterId) {
        Optional<User> user = userRepository.findById(requesterId);
        if (user.isPresent() && user.get().getGlobalPhoneHash() != null) {
            return user.get().getGlobalPhoneHash();
        }
        try {
            Profile requester = profileRepository.findByCustomerId(
                    Integer.valueOf(requesterId));
            if (!isConsistentActiveProfile(requester)) {
                throw ContactDiscoveryException.unavailable();
            }
            return protectedPhone(requester.getMobile());
        } catch (ContactDiscoveryException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw ContactDiscoveryException.unavailable();
        }
    }

    private String protectedPhone(String phone) {
        return phoneNumberHasher.hashWithGlobalPepper(digitsOnly(phone));
    }

    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private static final class ValidatedRequest {
        private final Map<String, String> canonicalToSubmitted;

        private ValidatedRequest(Map<String, String> canonicalToSubmitted) {
            this.canonicalToSubmitted = canonicalToSubmitted;
        }
    }
}
