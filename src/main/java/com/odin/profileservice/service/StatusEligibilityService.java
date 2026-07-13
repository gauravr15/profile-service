package com.odin.profileservice.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.odin.profileservice.dto.StatusEligibilityBatchResponse;
import com.odin.profileservice.dto.StatusEligibilityResult;
import com.odin.profileservice.entity.BlockedContact;
import com.odin.profileservice.entity.Contact;
import com.odin.profileservice.entity.ContactException;
import com.odin.profileservice.entity.Profile;
import com.odin.profileservice.entity.User;
import com.odin.profileservice.enums.ContactExceptionType;
import com.odin.profileservice.enums.StatusEligibilityDecision;
import com.odin.profileservice.enums.StatusEligibilityReason;
import com.odin.profileservice.repo.BlockedContactRepository;
import com.odin.profileservice.repo.ContactExceptionRepository;
import com.odin.profileservice.repo.ContactRepository;
import com.odin.profileservice.repo.ProfileRepository;
import com.odin.profileservice.repo.UserRepository;
import com.odin.profileservice.utility.AccountStateValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatusEligibilityService {

    public static final int MAX_BATCH_SIZE = 100;

    private final UserRepository userRepository;
    private final ContactRepository contactRepository;
    private final BlockedContactRepository blockedContactRepository;
    private final ContactExceptionRepository contactExceptionRepository;
    private final ProfileRepository profileRepository;
    private final AccountStateValidator accountStateValidator;
	private final StatusReadinessService statusReadinessService;

    public StatusEligibilityBatchResponse evaluateViewerAgainstUploaders(
            String viewerId, List<String> requestedUploaderIds) {
        long started = System.nanoTime();
        LinkedHashSet<String> uploaderIds = validateAndDeduplicate(viewerId, requestedUploaderIds);
        if (uploaderIds.isEmpty()) {
            return new StatusEligibilityBatchResponse(viewerId, Collections.emptyList());
        }

        try {
			com.odin.profileservice.dto.StatusReadinessResponse viewerReadiness = statusReadinessService.assess(viewerId);
			if (viewerReadiness.getState() == com.odin.profileservice.enums.StatusReadinessState.REPAIR_REQUIRED) {
				throw new StatusEligibilityRequestException(StatusEligibilityReason.STATUS_REPAIR_REQUIRED,
						"Status readiness repair is required");
			}
			if (viewerReadiness.getState() != com.odin.profileservice.enums.StatusReadinessState.READY) {
				throw new StatusEligibilityDependencyException("Status readiness is indeterminate", null);
			}
            LinkedHashSet<String> allUserIds = new LinkedHashSet<>(uploaderIds);
            allUserIds.add(viewerId);
            Map<String, User> users = indexUsers(userRepository.findAllById(allUserIds));
            User viewer = users.get(viewerId);
            if (viewer == null) {
                throw new StatusEligibilityRequestException(
                        StatusEligibilityReason.VIEWER_NOT_FOUND, "Viewer is not initialized");
            }

            Map<String, Profile> profiles = loadProfiles(allUserIds);
            Profile viewerProfile = profiles.get(viewerId);
            if (viewerProfile == null) {
                throw new StatusEligibilityRequestException(
                        StatusEligibilityReason.VIEWER_NOT_FOUND, "Viewer account was not found");
            }
            if (!accountStateValidator.isEligibleForAuth(viewerProfile)) {
                throw new StatusEligibilityRequestException(
                        StatusEligibilityReason.VIEWER_INACTIVE, "Viewer account is inactive");
            }

            if (hasAmbiguousHash(users.values(), viewer.getGlobalPhoneHash())) {
                return completed(viewerId, uploaderIds.stream()
                        .map(id -> result(id, StatusEligibilityDecision.INDETERMINATE,
                                StatusEligibilityReason.DATA_INTEGRITY_FAILURE))
                        .collect(Collectors.toList()), started);
            }

            Set<String> initializedUploaderIds = uploaderIds.stream()
                    .filter(id -> users.containsKey(id))
                    .collect(Collectors.toSet());
            String viewerHash = viewer.getGlobalPhoneHash();

            Set<String> forwardContacts = contactRepository
                    .findByOwnerUserIdInAndTargetGlobalPhoneHash(initializedUploaderIds, viewerHash)
                    .stream().map(Contact::getOwnerUserId).collect(Collectors.toSet());
            Set<String> blockedByUploaders = blockedContactRepository
                    .findByBlockerUserIdInAndBlockedGlobalPhoneHash(initializedUploaderIds, viewerHash)
                    .stream().map(BlockedContact::getBlockerUserId).collect(Collectors.toSet());

            Map<String, String> uploaderIdByHash = new HashMap<>();
            Set<String> ambiguousUploaderIds = new HashSet<>();
            for (String uploaderId : initializedUploaderIds) {
                String hash = users.get(uploaderId).getGlobalPhoneHash();
                String previous = uploaderIdByHash.put(hash, uploaderId);
                if (previous != null && !previous.equals(uploaderId)) {
                    ambiguousUploaderIds.add(previous);
                    ambiguousUploaderIds.add(uploaderId);
                }
            }

            Set<String> blockedByViewer = blockedContactRepository
                    .findByBlockerUserIdAndBlockedGlobalPhoneHashIn(viewerId, uploaderIdByHash.keySet())
                    .stream().map(BlockedContact::getBlockedGlobalPhoneHash)
                    .map(uploaderIdByHash::get).filter(id -> id != null).collect(Collectors.toSet());
            Set<String> explicitlyHidden = contactExceptionRepository
                    .findByOwnerUserIdInAndExceptionGlobalPhoneHash(initializedUploaderIds, viewerHash)
                    .stream().filter(e -> e.getExceptionType() == ContactExceptionType.ALWAYS_HIDE)
                    .map(ContactException::getOwnerUserId).collect(Collectors.toSet());

            List<StatusEligibilityResult> results = new ArrayList<>();
            for (String uploaderId : uploaderIds) {
                results.add(evaluateUploader(viewerId, uploaderId, users, profiles, forwardContacts,
                        blockedByUploaders, blockedByViewer, explicitlyHidden, ambiguousUploaderIds));
            }
            return completed(viewerId, results, started);
        } catch (StatusEligibilityRequestException exception) {
            throw exception;
        } catch (Exception dependencyFailure) {
            log.error("Status eligibility dependency failure. viewerId={}, requestedCount={}, category=DEPENDENCY_FAILURE",
                    viewerId, uploaderIds.size());
            throw new StatusEligibilityDependencyException(
                    "Status eligibility dependencies are unavailable", dependencyFailure);
        }
    }

    private StatusEligibilityResult evaluateUploader(String viewerId, String uploaderId,
            Map<String, User> users, Map<String, Profile> profiles, Set<String> forwardContacts,
            Set<String> blockedByUploaders, Set<String> blockedByViewer, Set<String> explicitlyHidden,
            Set<String> ambiguousUploaderIds) {
        if (viewerId.equals(uploaderId)) return deny(uploaderId, StatusEligibilityReason.SELF);

        Profile uploaderProfile = profiles.get(uploaderId);
        if (uploaderProfile == null) return deny(uploaderId, StatusEligibilityReason.UPLOADER_NOT_FOUND);
        if (!accountStateValidator.isEligibleForAuth(uploaderProfile)) {
            return deny(uploaderId, StatusEligibilityReason.UPLOADER_INACTIVE);
        }
        if (!users.containsKey(uploaderId)) {
            return result(uploaderId, StatusEligibilityDecision.INDETERMINATE,
                    StatusEligibilityReason.UNRESOLVED_CONTACT_TARGET);
        }
		if (statusReadinessService.assess(uploaderId).getState()
				!= com.odin.profileservice.enums.StatusReadinessState.READY) {
			return result(uploaderId, StatusEligibilityDecision.INDETERMINATE,
					StatusEligibilityReason.STATUS_REPAIR_REQUIRED);
		}
        if (ambiguousUploaderIds.contains(uploaderId)) {
            return result(uploaderId, StatusEligibilityDecision.INDETERMINATE,
                    StatusEligibilityReason.DATA_INTEGRITY_FAILURE);
        }
        if (blockedByUploaders.contains(uploaderId)) {
            return deny(uploaderId, StatusEligibilityReason.BLOCKED_BY_UPLOADER);
        }
        if (blockedByViewer.contains(uploaderId)) {
            return deny(uploaderId, StatusEligibilityReason.BLOCKED_BY_VIEWER);
        }
        if (explicitlyHidden.contains(uploaderId)) {
            return deny(uploaderId, StatusEligibilityReason.EXPLICITLY_HIDDEN);
        }
        if (!forwardContacts.contains(uploaderId)) {
            return deny(uploaderId, StatusEligibilityReason.NO_FORWARD_CONTACT);
        }
        return result(uploaderId, StatusEligibilityDecision.ALLOW,
                StatusEligibilityReason.ALLOWED_FORWARD_CONTACT);
    }

    private Map<String, User> indexUsers(List<User> loadedUsers) {
        Map<String, User> users = new HashMap<>();
        for (User user : loadedUsers) {
            if (users.put(user.getUserId(), user) != null) {
                throw new IllegalStateException("Duplicate middleware user ID");
            }
        }
        return users;
    }

    private Map<String, Profile> loadProfiles(Collection<String> userIds) {
        List<Integer> customerIds = userIds.stream().map(this::parseCustomerId).collect(Collectors.toList());
        List<Profile> loaded = profileRepository.findByCustomerIds(customerIds);
        Map<String, Profile> profiles = new HashMap<>();
        for (Profile profile : loaded) {
            String id = String.valueOf(profile.getCustomerId());
            if (profiles.put(id, profile) != null) {
                throw new IllegalStateException("Duplicate Core customer ID");
            }
        }
        return profiles;
    }

    private LinkedHashSet<String> validateAndDeduplicate(String viewerId, List<String> uploaderIds) {
        validateId(viewerId, "viewerId");
        if (uploaderIds == null) throw new IllegalArgumentException("uploaderIds is required");
        if (uploaderIds.size() > MAX_BATCH_SIZE) throw new IllegalArgumentException("uploaderIds exceeds batch limit");
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String uploaderId : uploaderIds) {
            validateId(uploaderId, "uploaderId");
            unique.add(uploaderId.trim());
        }
        return unique;
    }

    private void validateId(String id, String field) {
        if (id == null || !id.trim().matches("[0-9]{1,10}")) {
            throw new IllegalArgumentException(field + " must be a numeric customer ID");
        }
        parseCustomerId(id.trim());
    }

    private Integer parseCustomerId(String id) {
        try {
            return Integer.valueOf(id);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("customer ID is out of range", exception);
        }
    }

    private boolean hasAmbiguousHash(Collection<User> users, String viewerHash) {
        if (viewerHash == null || viewerHash.trim().isEmpty()) return true;
        return users.stream().filter(user -> viewerHash.equals(user.getGlobalPhoneHash())).count() != 1;
    }

    private StatusEligibilityBatchResponse completed(String viewerId, List<StatusEligibilityResult> results,
            long started) {
        long allowed = count(results, StatusEligibilityDecision.ALLOW);
        long denied = count(results, StatusEligibilityDecision.DENY);
        long indeterminate = count(results, StatusEligibilityDecision.INDETERMINATE);
        log.info("Status eligibility evaluated. viewerId={}, requestedCount={}, allowedCount={}, deniedCount={}, indeterminateCount={}, durationMs={}",
                viewerId, results.size(), allowed, denied, indeterminate,
                (System.nanoTime() - started) / 1_000_000L);
        return new StatusEligibilityBatchResponse(viewerId, results);
    }

    private long count(List<StatusEligibilityResult> results, StatusEligibilityDecision decision) {
        return results.stream().filter(result -> result.getDecision() == decision).count();
    }

    private StatusEligibilityResult deny(String uploaderId, StatusEligibilityReason reason) {
        return result(uploaderId, StatusEligibilityDecision.DENY, reason);
    }

    private StatusEligibilityResult result(String uploaderId, StatusEligibilityDecision decision,
            StatusEligibilityReason reason) {
        return new StatusEligibilityResult(uploaderId, decision, reason);
    }
}
