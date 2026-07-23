package com.odin.profileservice.service;

import com.odin.profileservice.dto.ContactSnapshotV2Request;
import com.odin.profileservice.dto.ContactSnapshotV2Response;
import com.odin.profileservice.entity.ContactSnapshotRequestV2;
import com.odin.profileservice.entity.ContactSnapshotRequestV2Id;
import com.odin.profileservice.entity.ContactSyncStateV2;
import com.odin.profileservice.entity.ContactV2;
import com.odin.profileservice.entity.Profile;
import com.odin.profileservice.entity.User;
import com.odin.profileservice.repo.ContactSnapshotRequestV2Repository;
import com.odin.profileservice.repo.ContactSnapshotOwnerLockV2Repository;
import com.odin.profileservice.repo.ContactSyncStateV2Repository;
import com.odin.profileservice.repo.ContactV2Repository;
import com.odin.profileservice.repo.ProfileRepository;
import com.odin.profileservice.repo.UserRepository;
import com.odin.profileservice.config.ContactTokenProperties;
import com.odin.profileservice.utility.PhoneNumberHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({
        ContactSnapshotV2Service.class,
        ContactSnapshotV2CommitService.class,
        ContactSnapshotOwnerLockV2Initializer.class,
        ContactSnapshotV2Digest.class,
        ContactTokenProperties.class,
        ContactTokenService.class,
        PhoneNumberHasher.class
})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "app.security.global-phone-pepper=test-only-contact-pepper",
        "app.contact.tokens.version-secrets.2=test-only-contact-token-version-2-change-me-32chars!!"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ContactSnapshotV2C3IntegrationTest {
    @Autowired
    private ContactSnapshotV2Service service;
    @Autowired
    private ContactSnapshotRequestV2Repository requests;
    @Autowired
    private ContactSyncStateV2Repository states;
    @Autowired
    private ContactV2Repository contacts;
    @Autowired
    private ContactSnapshotOwnerLockV2Repository locks;
    @Autowired
    private ContactSnapshotV2Digest digest;
    @Autowired
    private PhoneNumberHasher phoneNumberHasher;

    @MockBean
    private ProfileRepository profiles;
    @MockBean
    private UserRepository users;
    @MockBean
    private PrivacySettingsService privacy;
    @MockBean
    private SyncAuditService audit;
    @MockBean
    private PrivacyEvaluationService evaluation;

    @BeforeEach
    void setUp() {
        requests.deleteAll();
        contacts.deleteAll();
        states.deleteAll();
        locks.deleteAll();
        when(profiles.findByCustomerId(org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> {
                    int id = invocation.getArgument(0);
                    return Profile.builder().customerId(id)
                            .mobile("9199000000" + String.format("%02d", id % 100)).build();
                });
        when(profiles.findLikeMobileNumber(anyList(), org.mockito.ArgumentMatchers.eq(true)))
                .thenReturn(Collections.emptyList());
        when(users.findById(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> Optional.of(
                        User.builder().userId(invocation.getArgument(0)).build()));
    }

    @Test
    void exactRetryReplaysOriginalResponseWithoutMutationEvenAfterLaterRevision() {
        String firstId = UUID.randomUUID().toString();
        ContactSnapshotV2Response first =
                service.replaceSnapshot("59", request(firstId, 0,
                        "+919876543210", "+14155552671"));
        ContactSnapshotV2Response second = service.replaceSnapshot(
                "59", request(UUID.randomUUID().toString(), 1, "+14155552671"));

        ContactSnapshotV2Response replay =
                service.replaceSnapshot("59", request(firstId, 0,
                        "+1 415 555 2671", "9876543210", "+919876543210"));

        assertThat(second.getRevision()).isEqualTo(2);
        assertThat(replay).usingRecursiveComparison().isEqualTo(first);
        assertThat(states.findById("59")).get()
                .extracting(ContactSyncStateV2::getCurrentRevision).isEqualTo(2L);
        assertThat(contacts.findByOwnerUserId("59"))
                .extracting(ContactV2::getTargetGlobalPhoneHash)
                .hasSize(1);
    }

    @Test
    void sameSnapshotIdWithDifferentPayloadOrRevisionConflictsWithoutMutation() {
        String snapshotId = UUID.randomUUID().toString();
        ContactSnapshotV2Response committed =
                service.replaceSnapshot("59", request(snapshotId, 0, "+919876543210"));
        ContactSnapshotRequestV2 original = requests.findById(
                new ContactSnapshotRequestV2Id("59", snapshotId)).orElseThrow();

        assertThatThrownBy(() ->
                service.replaceSnapshot("59", request(snapshotId, 0, "+14155552671")))
                .isInstanceOf(ContactSnapshotV2ConflictException.class);
        assertThatThrownBy(() ->
                service.replaceSnapshot("59", request(snapshotId, 1, "+919876543210")))
                .isInstanceOf(ContactSnapshotV2ConflictException.class);

        assertThat(states.findById("59")).get()
                .extracting(ContactSyncStateV2::getCurrentRevision)
                .isEqualTo(committed.getRevision());
        assertThat(requests.findById(new ContactSnapshotRequestV2Id("59", snapshotId))).get()
                .extracting(ContactSnapshotRequestV2::getPayloadDigest)
                .isEqualTo(original.getPayloadDigest());
    }

    @Test
    void newSnapshotWithLowerOrHigherRevisionIsRejected() {
        service.replaceSnapshot("59", request(UUID.randomUUID().toString(), 0,
                "+919876543210"));

        assertThatThrownBy(() -> service.replaceSnapshot(
                "59", request(UUID.randomUUID().toString(), 0, "+14155552671")))
                .isInstanceOf(ContactSnapshotV2RevisionException.class);
        assertThatThrownBy(() -> service.replaceSnapshot(
                "59", request(UUID.randomUUID().toString(), 3, "+14155552671")))
                .isInstanceOf(ContactSnapshotV2RevisionException.class);
        assertThat(states.findById("59")).get()
                .extracting(ContactSyncStateV2::getCurrentRevision).isEqualTo(1L);
    }

    @Test
    void sameSnapshotUuidIsIndependentlyScopedPerAccount() {
        String snapshotId = UUID.randomUUID().toString();

        ContactSnapshotV2Response accountA =
                service.replaceSnapshot("59", request(snapshotId, 0, "+919876543210"));
        ContactSnapshotV2Response accountB =
                service.replaceSnapshot("72", request(snapshotId, 0, "+14155552671"));

        assertThat(accountA.getRevision()).isOne();
        assertThat(accountB.getRevision()).isOne();
        assertThat(requests.findById(new ContactSnapshotRequestV2Id("59", snapshotId))).isPresent();
        assertThat(requests.findById(new ContactSnapshotRequestV2Id("72", snapshotId))).isPresent();
    }

    @Test
    void twoConcurrentFirstSnapshotsProduceOneWinnerAndOneStaleResult() throws Exception {
        ConcurrentResults results = runConcurrently(
                () -> service.replaceSnapshot("74",
                        request(UUID.randomUUID().toString(), 0, "+919876543210")),
                () -> service.replaceSnapshot("74",
                        request(UUID.randomUUID().toString(), 0, "+14155552671")));

        assertOneSuccessAndOneFailure(results, ContactSnapshotV2RevisionException.class);
        assertThat(states.findById("74")).get()
                .extracting(ContactSyncStateV2::getCurrentRevision).isEqualTo(1L);
        assertThat(requests.findAll().stream()
                .filter(record -> record.getOwnerUserId().equals("74"))).hasSize(1);
        assertThat(contacts.findByOwnerUserId("74")).hasSize(1);
    }

    @Test
    void concurrentExactDuplicatesCommitOnceAndReturnEquivalentSuccess() throws Exception {
        String snapshotId = UUID.randomUUID().toString();
        ConcurrentResults results = runConcurrently(
                () -> service.replaceSnapshot("63",
                        request(snapshotId, 0, "+919876543210")),
                () -> service.replaceSnapshot("63",
                        request(snapshotId, 0, "9876543210", "+919876543210")));

        assertThat(results.firstFailure).isNull();
        assertThat(results.secondFailure).isNull();
        assertThat(results.first).usingRecursiveComparison().isEqualTo(results.second);
        assertThat(states.findById("63")).get()
                .extracting(ContactSyncStateV2::getCurrentRevision).isEqualTo(1L);
        assertThat(requests.findAll().stream()
                .filter(record -> record.getOwnerUserId().equals("63"))).hasSize(1);
    }

    @Test
    void concurrentSameIdDifferentPayloadCannotBothSucceed() throws Exception {
        String snapshotId = UUID.randomUUID().toString();
        ConcurrentResults results = runConcurrently(
                () -> service.replaceSnapshot("70",
                        request(snapshotId, 0, "+919876543210")),
                () -> service.replaceSnapshot("70",
                        request(snapshotId, 0, "+14155552671")));

        assertOneSuccessAndOneFailure(results, ContactSnapshotV2ConflictException.class);
        assertThat(states.findById("70")).get()
                .extracting(ContactSyncStateV2::getCurrentRevision).isEqualTo(1L);
        assertThat(contacts.findByOwnerUserId("70")).hasSize(1);
    }

    @Test
    void oldReplayResponsesCompactToSafeDigestTombstones() {
        Timestamp old = Timestamp.from(Instant.now().minus(
                ContactSnapshotV2CommitService.FULL_REPLAY_RETENTION).minusSeconds(1));
        for (int index = 0; index < 101; index++) {
            requests.save(record("59", new UUID(0, index + 1).toString(), old, index + 1));
        }

        service.replaceSnapshot("59",
                request(UUID.randomUUID().toString(), 0, "+919876543210"));

        List<ContactSnapshotRequestV2> records =
                requests.findByOwnerUserIdOrderByCreatedAtDesc("59");
        assertThat(records.stream()
                .filter(value -> value.getStatus() == ContactSnapshotRequestV2.Status.TOMBSTONE))
                .isNotEmpty()
                .allSatisfy(value -> {
                    assertThat(value.getPayloadDigest()).hasSize(64);
                    assertThat(value.getSyncedAt()).isNull();
                    assertThat(value.getSubmittedCount()).isNull();
                });
        ContactSnapshotRequestV2 tombstone = records.stream()
                .filter(value -> value.getStatus() == ContactSnapshotRequestV2.Status.TOMBSTONE)
                .findFirst().orElseThrow();
        assertThatThrownBy(() -> service.replaceSnapshot("59",
                request(tombstone.getSnapshotId(), tombstone.getBaseRevision(),
                        "+919876543210")))
                .isInstanceOf(ContactSnapshotV2ReplayExpiredException.class);
    }

    private ContactSnapshotRequestV2 record(
            String owner,
            String snapshotId,
            Timestamp createdAt,
            long revision) {
        return ContactSnapshotRequestV2.builder()
                .ownerUserId(owner)
                .snapshotId(snapshotId)
                .payloadDigest(digest.compute(revision - 1, "IN",
                        Collections.singletonList(
                                phoneNumberHasher.hashWithGlobalPepper("919876543210"))))
                .baseRevision(revision - 1)
                .committedRevision(revision)
                .status(ContactSnapshotRequestV2.Status.COMMITTED)
                .submittedCount(1).canonicalCount(1).addedCount(1).removedCount(0)
                .retainedCount(0).registeredCount(0).unregisteredCount(1).rejectedCount(0)
                .syncedAt(createdAt).createdAt(createdAt).updatedAt(createdAt)
                .build();
    }

    private void assertOneSuccessAndOneFailure(
            ConcurrentResults results,
            Class<? extends Throwable> failureType) {
        List<ContactSnapshotV2Response> successes =
                Arrays.asList(results.first, results.second);
        List<Throwable> failures = Arrays.asList(results.firstFailure, results.secondFailure);
        assertThat(successes).filteredOn(value -> value != null).hasSize(1);
        assertThat(failures).filteredOn(value -> value != null)
                .singleElement().isInstanceOf(failureType);
    }

    private ConcurrentResults runConcurrently(
            Callable<ContactSnapshotV2Response> firstCall,
            Callable<ContactSnapshotV2Response> secondCall) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Outcome> first = executor.submit(() -> invoke(start, firstCall));
            Future<Outcome> second = executor.submit(() -> invoke(start, secondCall));
            start.countDown();
            Outcome firstOutcome = first.get();
            Outcome secondOutcome = second.get();
            return new ConcurrentResults(
                    firstOutcome.response, secondOutcome.response,
                    firstOutcome.failure, secondOutcome.failure);
        } finally {
            executor.shutdownNow();
        }
    }

    private Outcome invoke(
            CountDownLatch start,
            Callable<ContactSnapshotV2Response> operation) {
        try {
            start.await();
            return new Outcome(operation.call(), null);
        } catch (Throwable failure) {
            return new Outcome(null, failure);
        }
    }

    private ContactSnapshotV2Request request(
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

    private static final class Outcome {
        private final ContactSnapshotV2Response response;
        private final Throwable failure;

        private Outcome(ContactSnapshotV2Response response, Throwable failure) {
            this.response = response;
            this.failure = failure;
        }
    }

    private static final class ConcurrentResults {
        private final ContactSnapshotV2Response first;
        private final ContactSnapshotV2Response second;
        private final Throwable firstFailure;
        private final Throwable secondFailure;

        private ConcurrentResults(
                ContactSnapshotV2Response first,
                ContactSnapshotV2Response second,
                Throwable firstFailure,
                Throwable secondFailure) {
            this.first = first;
            this.second = second;
            this.firstFailure = firstFailure;
            this.secondFailure = secondFailure;
        }
    }
}
