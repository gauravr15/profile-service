package com.odin.profileservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import com.odin.profileservice.enums.StatusReadinessState;
import com.odin.profileservice.repo.BlockedContactRepository;
import com.odin.profileservice.repo.ContactExceptionRepository;
import com.odin.profileservice.repo.ContactRepository;
import com.odin.profileservice.repo.ProfileRepository;
import com.odin.profileservice.repo.UserRepository;
import com.odin.profileservice.utility.AccountStateValidator;

class StatusEligibilityServiceTest {

    private UserRepository users;
    private ContactRepository contacts;
    private BlockedContactRepository blocks;
    private ContactExceptionRepository exceptions;
    private ProfileRepository profiles;
    private StatusEligibilityService service;
	private StatusReadinessService readiness;
    private final Set<String> forwardOwners = new HashSet<>();
    private final Set<String> uploaderBlockers = new HashSet<>();
    private final Set<String> viewerBlockedHashes = new HashSet<>();
    private final Set<String> hiddenOwners = new HashSet<>();
    private List<User> loadedUsers;
    private List<Profile> loadedProfiles;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        contacts = mock(ContactRepository.class);
        blocks = mock(BlockedContactRepository.class);
        exceptions = mock(ContactExceptionRepository.class);
        profiles = mock(ProfileRepository.class);
		readiness = mock(StatusReadinessService.class);
		when(readiness.assess(anyString())).thenReturn(com.odin.profileservice.dto.StatusReadinessResponse.builder()
				.state(StatusReadinessState.READY).reasons(Collections.emptyList())
				.repairActions(Collections.emptyList()).build());
        service = new StatusEligibilityService(users, contacts, blocks, exceptions, profiles,
                new AccountStateValidator(), readiness);

        loadedUsers = Arrays.asList(user("1", "hash-viewer"), user("2", "hash-2"),
                user("3", "hash-3"), user("4", "hash-4"));
        loadedProfiles = Arrays.asList(profile(1, true, false), profile(2, true, false),
                profile(3, true, false), profile(4, true, false));
        when(users.findAllById(any())).thenAnswer(invocation -> loadedUsers);
        when(profiles.findByCustomerIds(any())).thenAnswer(invocation -> loadedProfiles);
        when(contacts.findByOwnerUserIdInAndTargetGlobalPhoneHash(any(), anyString()))
                .thenAnswer(invocation -> forwardContacts());
        when(blocks.findByBlockerUserIdInAndBlockedGlobalPhoneHash(any(), anyString()))
                .thenAnswer(invocation -> uploaderBlocks());
        when(blocks.findByBlockerUserIdAndBlockedGlobalPhoneHashIn(anyString(), any()))
                .thenAnswer(invocation -> viewerBlocks());
        when(exceptions.findByOwnerUserIdInAndExceptionGlobalPhoneHash(any(), anyString()))
                .thenAnswer(invocation -> hiddenExceptions());
    }

    @Test
    void forwardContactAllowsReverseOnlyDeniesAndOrderingIsDeduplicated() {
        forwardOwners.add("2");

        StatusEligibilityBatchResponse response = service.evaluateViewerAgainstUploaders(
                "1", Arrays.asList("2", "3", "2", "1"));

        assertEquals(Arrays.asList("2", "3", "1"), uploaderIds(response));
        assertDecision(response, "2", StatusEligibilityDecision.ALLOW,
                StatusEligibilityReason.ALLOWED_FORWARD_CONTACT);
        assertDecision(response, "3", StatusEligibilityDecision.DENY,
                StatusEligibilityReason.NO_FORWARD_CONTACT);
        assertDecision(response, "1", StatusEligibilityDecision.DENY, StatusEligibilityReason.SELF);
        verify(users, times(1)).findAllById(any());
        verify(profiles, times(1)).findByCustomerIds(any());
        verify(contacts, times(1)).findByOwnerUserIdInAndTargetGlobalPhoneHash(any(), anyString());
        verify(blocks, times(1)).findByBlockerUserIdInAndBlockedGlobalPhoneHash(any(), anyString());
        verify(blocks, times(1)).findByBlockerUserIdAndBlockedGlobalPhoneHashIn(anyString(), any());
        verify(exceptions, times(1)).findByOwnerUserIdInAndExceptionGlobalPhoneHash(any(), anyString());
    }

    @Test
    void bilateralBlocksAndExplicitHideOverrideForwardContact() {
        forwardOwners.addAll(Arrays.asList("2", "3", "4"));
        uploaderBlockers.add("2");
        viewerBlockedHashes.add("hash-3");
        hiddenOwners.add("4");

        StatusEligibilityBatchResponse response = service.evaluateViewerAgainstUploaders(
                "1", Arrays.asList("2", "3", "4"));

        assertDecision(response, "2", StatusEligibilityDecision.DENY,
                StatusEligibilityReason.BLOCKED_BY_UPLOADER);
        assertDecision(response, "3", StatusEligibilityDecision.DENY,
                StatusEligibilityReason.BLOCKED_BY_VIEWER);
        assertDecision(response, "4", StatusEligibilityDecision.DENY,
                StatusEligibilityReason.EXPLICITLY_HIDDEN);
    }

    @Test
    void decisionsUseCurrentContactAndBlockStateOnEveryCall() {
        assertDecision(service.evaluateViewerAgainstUploaders("1", Collections.singletonList("2")), "2",
                StatusEligibilityDecision.DENY, StatusEligibilityReason.NO_FORWARD_CONTACT);
        forwardOwners.add("2");
        assertDecision(service.evaluateViewerAgainstUploaders("1", Collections.singletonList("2")), "2",
                StatusEligibilityDecision.ALLOW, StatusEligibilityReason.ALLOWED_FORWARD_CONTACT);
        uploaderBlockers.add("2");
        assertDecision(service.evaluateViewerAgainstUploaders("1", Collections.singletonList("2")), "2",
                StatusEligibilityDecision.DENY, StatusEligibilityReason.BLOCKED_BY_UPLOADER);
        uploaderBlockers.clear();
        assertDecision(service.evaluateViewerAgainstUploaders("1", Collections.singletonList("2")), "2",
                StatusEligibilityDecision.ALLOW, StatusEligibilityReason.ALLOWED_FORWARD_CONTACT);
        forwardOwners.clear();
        assertDecision(service.evaluateViewerAgainstUploaders("1", Collections.singletonList("2")), "2",
                StatusEligibilityDecision.DENY, StatusEligibilityReason.NO_FORWARD_CONTACT);
    }

    @Test
    void inactiveOrMissingUploaderDeniesAndUninitializedUploaderIsIndeterminate() {
        loadedProfiles = Arrays.asList(profile(1, true, false), profile(2, false, false),
                profile(4, true, false));
        loadedUsers = Arrays.asList(user("1", "hash-viewer"), user("2", "hash-2"));
		when(readiness.assess("2")).thenReturn(com.odin.profileservice.dto.StatusReadinessResponse.builder()
				.state(StatusReadinessState.REPAIR_REQUIRED).reasons(Collections.emptyList())
				.repairActions(Collections.emptyList()).build());

        StatusEligibilityBatchResponse response = service.evaluateViewerAgainstUploaders(
                "1", Arrays.asList("2", "3", "4"));

        assertDecision(response, "2", StatusEligibilityDecision.DENY,
                StatusEligibilityReason.UPLOADER_INACTIVE);
        assertDecision(response, "3", StatusEligibilityDecision.DENY,
                StatusEligibilityReason.UPLOADER_NOT_FOUND);
        assertDecision(response, "4", StatusEligibilityDecision.INDETERMINATE,
                StatusEligibilityReason.UNRESOLVED_CONTACT_TARGET);
		verify(readiness, never()).assess("2");
    }

    @Test
    void missingOrInactiveViewerFailsWholeRequest() {
        loadedUsers = Collections.singletonList(user("2", "hash-2"));
        StatusEligibilityRequestException missing = assertThrows(StatusEligibilityRequestException.class,
                () -> service.evaluateViewerAgainstUploaders("1", Collections.singletonList("2")));
        assertEquals(StatusEligibilityReason.VIEWER_NOT_FOUND, missing.getReason());

        setUp();
        loadedProfiles = Arrays.asList(profile(1, false, false), profile(2, true, false));
        StatusEligibilityRequestException inactive = assertThrows(StatusEligibilityRequestException.class,
                () -> service.evaluateViewerAgainstUploaders("1", Collections.singletonList("2")));
        assertEquals(StatusEligibilityReason.VIEWER_INACTIVE, inactive.getReason());
    }

    @Test
    void ambiguousViewerHashMakesEveryResultIndeterminate() {
        loadedUsers = Arrays.asList(user("1", "same-hash"), user("2", "same-hash"));
        loadedProfiles = Arrays.asList(profile(1, true, false), profile(2, true, false));

        StatusEligibilityBatchResponse response = service.evaluateViewerAgainstUploaders(
                "1", Collections.singletonList("2"));

        assertDecision(response, "2", StatusEligibilityDecision.INDETERMINATE,
                StatusEligibilityReason.DATA_INTEGRITY_FAILURE);
        verify(contacts, never()).findByOwnerUserIdInAndTargetGlobalPhoneHash(any(), anyString());
    }

    @Test
    void repositoryFailureIsRetryableAndNeverAllows() {
        when(users.findAllById(any())).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(StatusEligibilityDependencyException.class,
                () -> service.evaluateViewerAgainstUploaders("1", Collections.singletonList("2")));
    }

    @Test
    void validatesBatchShapeAndAllowsEmptyBatch() {
        assertEquals(0, service.evaluateViewerAgainstUploaders("1", Collections.emptyList())
                .getResults().size());
        assertThrows(IllegalArgumentException.class,
                () -> service.evaluateViewerAgainstUploaders("1", null));
        assertThrows(IllegalArgumentException.class,
                () -> service.evaluateViewerAgainstUploaders("1", Collections.singletonList(" ")));
        List<String> oversized = new ArrayList<>();
        for (int i = 0; i <= StatusEligibilityService.MAX_BATCH_SIZE; i++) oversized.add(String.valueOf(i + 2));
        assertThrows(IllegalArgumentException.class,
                () -> service.evaluateViewerAgainstUploaders("1", oversized));
    }

    private List<Contact> forwardContacts() {
        return forwardOwners.stream().map(owner -> Contact.builder().ownerUserId(owner)
                .targetGlobalPhoneHash("hash-viewer").build()).collect(java.util.stream.Collectors.toList());
    }

    private List<BlockedContact> uploaderBlocks() {
        return uploaderBlockers.stream().map(owner -> BlockedContact.builder().blockerUserId(owner)
                .blockedGlobalPhoneHash("hash-viewer").build()).collect(java.util.stream.Collectors.toList());
    }

    private List<BlockedContact> viewerBlocks() {
        return viewerBlockedHashes.stream().map(hash -> BlockedContact.builder().blockerUserId("1")
                .blockedGlobalPhoneHash(hash).build()).collect(java.util.stream.Collectors.toList());
    }

    private List<ContactException> hiddenExceptions() {
        return hiddenOwners.stream().map(owner -> ContactException.builder().ownerUserId(owner)
                .exceptionGlobalPhoneHash("hash-viewer").exceptionType(ContactExceptionType.ALWAYS_HIDE).build())
                .collect(java.util.stream.Collectors.toList());
    }

    private User user(String id, String hash) {
        return User.builder().userId(id).globalPhoneHash(hash).build();
    }

    private Profile profile(int id, boolean active, boolean deleted) {
        return Profile.builder().customerId(id).isActive(active).isDeleted(deleted).build();
    }

    private List<String> uploaderIds(StatusEligibilityBatchResponse response) {
        return response.getResults().stream().map(StatusEligibilityResult::getUploaderId)
                .collect(java.util.stream.Collectors.toList());
    }

    private void assertDecision(StatusEligibilityBatchResponse response, String uploaderId,
            StatusEligibilityDecision decision, StatusEligibilityReason reason) {
        StatusEligibilityResult result = response.getResults().stream()
                .filter(item -> uploaderId.equals(item.getUploaderId())).findFirst().orElseThrow();
        assertEquals(decision, result.getDecision());
        assertEquals(reason, result.getReason());
    }
}
