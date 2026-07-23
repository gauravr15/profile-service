package com.odin.profileservice.service;

import com.odin.profileservice.dto.ContactSnapshotV2Request;
import com.odin.profileservice.dto.ContactSnapshotV2Response;
import com.odin.profileservice.entity.Profile;
import com.odin.profileservice.repo.ContactSnapshotOwnerLockV2Repository;
import com.odin.profileservice.repo.ContactSnapshotRequestV2Repository;
import com.odin.profileservice.repo.ProfileRepository;
import com.odin.profileservice.utility.PhoneNumberHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContactSnapshotV2ServiceTest {
    private final ContactSnapshotRequestV2Repository requests =
            mock(ContactSnapshotRequestV2Repository.class);
    private final ContactSnapshotOwnerLockV2Repository locks =
            mock(ContactSnapshotOwnerLockV2Repository.class);
    private final ContactSnapshotOwnerLockV2Initializer lockInitializer =
            mock(ContactSnapshotOwnerLockV2Initializer.class);
    private final ContactSnapshotV2CommitService commit =
            mock(ContactSnapshotV2CommitService.class);
    private final ProfileRepository profiles = mock(ProfileRepository.class);
    private final PhoneNumberHasher hasher = new PhoneNumberHasher();
    private final ContactTokenService tokens = mock(ContactTokenService.class);
    private final ContactSnapshotV2Digest digest = new ContactSnapshotV2Digest();

    private ContactSnapshotV2Service service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(hasher, "globalPhonePepper", "test-only-contact-pepper");
        when(tokens.deriveLookupTokensFromCanonical(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> new ContactTokenService.LookupTokenMaterial(
                        invocation.getArgument(0), "legacy-token", "current-token", 1));
        service = new ContactSnapshotV2Service(
                requests, locks, lockInitializer, commit, digest, profiles, hasher, tokens);
        when(profiles.findByCustomerId(59)).thenReturn(Profile.builder()
                .customerId(59).mobile("919900000059").build());
        when(profiles.findLikeMobileNumber(anyList(), org.mockito.ArgumentMatchers.eq(true)))
                .thenReturn(Collections.emptyList());
        when(locks.existsById("59")).thenReturn(true);
        when(commit.commit(any(), any())).thenAnswer(invocation -> {
            CanonicalContactSnapshotV2 snapshot = invocation.getArgument(1);
            return response(snapshot.getSnapshotId(), 1, snapshot.getSubmittedCount(),
                    snapshot.getTargetTokens().size(), Instant.parse("2026-07-23T00:00:00Z"));
        });
    }

    @Test
    void canonicalizesAndDeduplicatesBeforeCommit() {
        ContactSnapshotV2Response result = service.replaceSnapshot("59", request(0,
                "+91 98765 43210", "9876543210", "+1 415 555 2671"));

        assertThat(result.getSubmittedCount()).isEqualTo(3);
        assertThat(result.getCanonicalCount()).isEqualTo(2);
        verify(commit).commit(org.mockito.ArgumentMatchers.eq("59"),
                org.mockito.ArgumentMatchers.argThat(snapshot ->
                        snapshot.getTargetTokens().size() == 2));
    }

    @Test
    void committedRequestReplaysThroughLifecycleAwareCommit() {
        String snapshotId = UUID.randomUUID().toString();
        Instant originalTime = Instant.parse("2026-07-22T00:00:00Z");
        doReturn(response(snapshotId, 4, 1, 1, originalTime))
                .when(commit).commit(any(), any());

        ContactSnapshotV2Response replay =
                service.replaceSnapshot("59", requestWithId(snapshotId, 0, "+919876543210"));

        assertThat(replay.getRevision()).isEqualTo(4);
        assertThat(replay.getSyncedAt()).isEqualTo(originalTime);
        verify(profiles).findByCustomerId(59);
        verify(commit).commit(org.mockito.ArgumentMatchers.eq("59"), any());
    }

    @Test
    void malformedEntryRejectsBeforeDependencies() {
        assertThatThrownBy(() -> service.replaceSnapshot("59", request(0, "not-a-phone")))
                .isInstanceOf(ContactSnapshotV2ValidationException.class);
        verify(profiles, never()).findByCustomerId(any());
        verify(commit, never()).commit(any(), any());
    }

    private ContactSnapshotV2Response response(
            String snapshotId,
            long revision,
            int submitted,
            int canonical,
            Instant syncedAt) {
        return ContactSnapshotV2Response.builder()
                .snapshotId(snapshotId).revision(revision)
                .submittedCount(submitted).canonicalCount(canonical)
                .addedCount(canonical).removedCount(0).retainedCount(0)
                .registeredCount(0).unregisteredCount(canonical).rejectedCount(0)
                .syncedAt(syncedAt).build();
    }

    private ContactSnapshotV2Request request(long revision, String... phones) {
        return requestWithId(UUID.randomUUID().toString(), revision, phones);
    }

    private ContactSnapshotV2Request requestWithId(
            String snapshotId,
            long revision,
            String... phones) {
        return ContactSnapshotV2Request.builder()
                .snapshotId(snapshotId)
                .baseRevision(revision)
                .countryCode("IN")
                .contacts(Arrays.stream(phones)
                        .map(phone -> ContactSnapshotV2Request.ContactItem.builder()
                                .phoneNumber(phone).build())
                        .collect(java.util.stream.Collectors.toList()))
                .build();
    }
}
