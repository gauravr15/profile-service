package com.odin.profileservice.service;

import com.odin.profileservice.dto.ContactSnapshotUploadChunkV2Request;
import com.odin.profileservice.dto.ContactSnapshotUploadChunkV2Response;
import com.odin.profileservice.dto.ContactSnapshotUploadSessionV2Request;
import com.odin.profileservice.dto.ContactSnapshotUploadSessionV2Response;
import com.odin.profileservice.dto.ContactSnapshotV2Request;
import com.odin.profileservice.dto.ContactSnapshotV2Response;
import com.odin.profileservice.entity.ContactSnapshotUploadSessionV2;
import com.odin.profileservice.entity.Profile;
import com.odin.profileservice.entity.User;
import com.odin.profileservice.repo.ContactSnapshotOwnerLockV2Repository;
import com.odin.profileservice.repo.ContactSnapshotRequestV2Repository;
import com.odin.profileservice.repo.ContactSnapshotUploadChunkV2Repository;
import com.odin.profileservice.repo.ContactSnapshotUploadSessionV2Repository;
import com.odin.profileservice.repo.ContactSnapshotUploadTargetV2Repository;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@DataJpaTest(showSql = false)
@Import({
        ContactSnapshotV2Service.class,
        ContactSnapshotV2CommitService.class,
        ContactSnapshotOwnerLockV2Initializer.class,
        ContactSnapshotV2Digest.class,
        ContactSnapshotUploadV2Service.class,
        ContactSnapshotUploadTransactionV2Service.class,
        ContactSnapshotUploadCleanupV2.class,
        ContactSnapshotUploadCleanupWorkerV2.class,
        ContactTokenProperties.class,
        ContactTokenService.class,
        PhoneNumberHasher.class
})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF",
        "app.security.global-phone-pepper=test-only-contact-pepper",
        "app.contact.tokens.version-secrets.2=test-only-contact-token-version-2-change-me-32chars!!"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ContactSnapshotUploadV2IntegrationTest {
    private static final String OWNER = "59";

    @Autowired
    private ContactSnapshotUploadV2Service service;
    @Autowired
    private ContactSnapshotUploadSessionV2Repository sessions;
    @Autowired
    private ContactSnapshotUploadChunkV2Repository chunks;
    @Autowired
    private ContactSnapshotUploadTargetV2Repository targets;
    @Autowired
    private ContactSnapshotRequestV2Repository requests;
    @Autowired
    private ContactV2Repository contacts;
    @Autowired
    private ContactSyncStateV2Repository states;
    @Autowired
    private ContactSnapshotOwnerLockV2Repository locks;
    @Autowired
    private ContactSnapshotUploadCleanupV2 cleanup;

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
        targets.deleteAll();
        chunks.deleteAll();
        sessions.deleteAll();
        requests.deleteAll();
        contacts.deleteAll();
        states.deleteAll();
        locks.deleteAll();
        when(profiles.findByCustomerId(org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> Profile.builder()
                        .customerId(invocation.getArgument(0))
                        .mobile("919900000059")
                        .build());
        when(profiles.findLikeMobileNumber(anyList(), org.mockito.ArgumentMatchers.eq(true)))
                .thenReturn(Collections.emptyList());
        when(users.findById(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> Optional.of(
                        User.builder().userId(invocation.getArgument(0)).build()));
    }

    @Test
    void chunksAreOutOfOrderIdempotentDeduplicatedAndInvisibleUntilCommit() {
        List<String> first = phones(0, 500);
        String duplicate = first.get(0);
        ContactSnapshotUploadSessionV2Response session =
                create(501, 2, UUID.randomUUID().toString(), 0);

        service.uploadChunk(OWNER, session.getSessionId(), 1, chunk(duplicate));
        assertThat(contacts.findByOwnerUserId(OWNER)).isEmpty();
        ContactSnapshotUploadChunkV2Response accepted =
                service.uploadChunk(OWNER, session.getSessionId(), 0, chunk(first));
        ContactSnapshotUploadChunkV2Response replay =
                service.uploadChunk(OWNER, session.getSessionId(), 0,
                        chunk(new ArrayList<>(first)));

        assertThat(replay).usingRecursiveComparison().isEqualTo(accepted);
        assertThat(targets.findBySessionIdOrderByTargetGlobalPhoneHash(
                session.getSessionId())).hasSize(500);
        assertThatThrownBy(() -> service.uploadChunk(
                OWNER, session.getSessionId(), 0, chunk(phones(600, 500))))
                .isInstanceOf(ContactSnapshotUploadV2Exception.class)
                .extracting("reason")
                .isEqualTo(ContactSnapshotUploadV2Exception.Reason.CHUNK_INDEX_REUSE_CONFLICT);

        ContactSnapshotV2Response committed = service.commit(OWNER, session.getSessionId());

        assertThat(committed.getSubmittedCount()).isEqualTo(501);
        assertThat(committed.getCanonicalCount()).isEqualTo(500);
        assertThat(contacts.findByOwnerUserId(OWNER)).hasSize(500);
        assertThat(chunks.findBySessionIdOrderByChunkIndex(session.getSessionId())).isEmpty();
        assertThat(targets.findBySessionIdOrderByTargetGlobalPhoneHash(
                session.getSessionId())).isEmpty();
        ContactSnapshotV2Response commitReplay = service.commit(OWNER, session.getSessionId());
        assertThat(commitReplay).usingRecursiveComparison().isEqualTo(committed);
        assertThat(states.findById(OWNER)).get()
                .extracting(value -> value.getCurrentRevision()).isEqualTo(1L);
    }

    @Test
    void invalidIncompleteAndMismatchedSessionsCannotChangeActiveState() {
        assertThatThrownBy(() -> service.create(OWNER, sessionRequest(
                0, 1, UUID.randomUUID().toString(), 0)))
                .isInstanceOf(ContactSnapshotUploadV2Exception.class);
        assertThatThrownBy(() -> service.create(OWNER, sessionRequest(
                10_001, 21, UUID.randomUUID().toString(), 0)))
                .isInstanceOf(ContactSnapshotUploadV2Exception.class);

        ContactSnapshotUploadSessionV2Response incomplete =
                create(501, 2, UUID.randomUUID().toString(), 0);
        service.uploadChunk(OWNER, incomplete.getSessionId(), 0, chunk(phones(0, 500)));
        assertThatThrownBy(() -> service.commit(OWNER, incomplete.getSessionId()))
                .isInstanceOf(ContactSnapshotUploadV2Exception.class)
                .extracting("reason")
                .isEqualTo(ContactSnapshotUploadV2Exception.Reason.CHUNK_COUNT_INCOMPLETE);

        ContactSnapshotUploadSessionV2Response mismatch =
                create(501, 2, UUID.randomUUID().toString(), 0);
        service.uploadChunk(OWNER, mismatch.getSessionId(), 0, chunk(phones(0, 499)));
        service.uploadChunk(OWNER, mismatch.getSessionId(), 1, chunk(phone(700)));
        assertThatThrownBy(() -> service.commit(OWNER, mismatch.getSessionId()))
                .isInstanceOf(ContactSnapshotUploadV2Exception.class)
                .extracting("reason")
                .isEqualTo(ContactSnapshotUploadV2Exception.Reason.DECLARED_TOTAL_MISMATCH);
        assertThat(contacts.findByOwnerUserId(OWNER)).isEmpty();
        assertThat(states.findById(OWNER)).isEmpty();
        assertThat(requests.findAll()).isEmpty();
    }

    @Test
    void creationIsIdempotentBoundedAndAccountScoped() {
        String snapshotId = UUID.randomUUID().toString();
        ContactSnapshotUploadSessionV2Response first =
                create(1, 1, snapshotId, 0);
        ContactSnapshotUploadSessionV2Response replay =
                create(1, 1, snapshotId, 0);
        assertThat(replay).usingRecursiveComparison().isEqualTo(first);
        assertThatThrownBy(() -> create(500, 1, snapshotId, 0))
                .isInstanceOf(ContactSnapshotUploadV2Exception.class)
                .extracting("reason")
                .isEqualTo(ContactSnapshotUploadV2Exception.Reason.SESSION_CONFLICT);

        create(1, 1, UUID.randomUUID().toString(), 0);
        create(1, 1, UUID.randomUUID().toString(), 0);
        assertThatThrownBy(() -> create(1, 1, UUID.randomUUID().toString(), 0))
                .isInstanceOf(ContactSnapshotUploadV2Exception.class)
                .extracting("reason")
                .isEqualTo(ContactSnapshotUploadV2Exception.Reason.SESSION_LIMIT);

        ContactSnapshotUploadSessionV2Response otherAccount = service.create(
                "72", sessionRequest(1, 1, snapshotId, 0));
        assertThat(otherAccount.getSessionId()).isNotEqualTo(first.getSessionId());
    }

    @Test
    void ownershipCancellationAndExpiryAreIsolatedAndCleanupIsBounded() {
        ContactSnapshotUploadSessionV2Response session =
                create(1, 1, UUID.randomUUID().toString(), 0);
        assertThatThrownBy(() -> service.uploadChunk(
                "72", session.getSessionId(), 0, chunk(phone(0))))
                .isInstanceOf(ContactSnapshotUploadV2Exception.class)
                .extracting("reason")
                .isEqualTo(ContactSnapshotUploadV2Exception.Reason.SESSION_NOT_FOUND);
        service.uploadChunk(OWNER, session.getSessionId(), 0, chunk(phone(0)));

        ContactSnapshotUploadSessionV2 stored =
                sessions.findById(session.getSessionId()).orElseThrow();
        stored.setExpiresAt(Timestamp.from(Instant.now().minusSeconds(1)));
        sessions.saveAndFlush(stored);
        assertThatThrownBy(() -> service.commit(OWNER, session.getSessionId()))
                .isInstanceOf(ContactSnapshotUploadV2Exception.class)
                .extracting("reason")
                .isEqualTo(ContactSnapshotUploadV2Exception.Reason.SESSION_EXPIRED);

        cleanup.removeExpiredSessions();
        assertThat(sessions.findById(session.getSessionId())).isEmpty();
        assertThat(chunks.findBySessionIdOrderByChunkIndex(session.getSessionId())).isEmpty();
        assertThat(targets.findBySessionIdOrderByTargetGlobalPhoneHash(
                session.getSessionId())).isEmpty();
        cleanup.removeExpiredSessions();

        ContactSnapshotUploadSessionV2Response cancelled =
                create(1, 1, UUID.randomUUID().toString(), 0);
        service.uploadChunk(OWNER, cancelled.getSessionId(), 0, chunk(phone(1)));
        service.cancel(OWNER, cancelled.getSessionId());
        service.cancel(OWNER, cancelled.getSessionId());
        assertThat(contacts.findByOwnerUserId(OWNER)).isEmpty();
    }

    @Test
    void twoSessionsFromSameRevisionHaveOneCommitWinner() throws Exception {
        ContactSnapshotUploadSessionV2Response first =
                create(1, 1, UUID.randomUUID().toString(), 0);
        ContactSnapshotUploadSessionV2Response second =
                create(1, 1, UUID.randomUUID().toString(), 0);
        service.uploadChunk(OWNER, first.getSessionId(), 0, chunk(phone(0)));
        service.uploadChunk(OWNER, second.getSessionId(), 0, chunk(phone(1)));

        ConcurrentResults results = runConcurrently(
                () -> service.commit(OWNER, first.getSessionId()),
                () -> service.commit(OWNER, second.getSessionId()));

        assertThat(Arrays.asList(results.firstFailure, results.secondFailure))
                .filteredOn(value -> value != null)
                .singleElement().isInstanceOf(ContactSnapshotV2RevisionException.class);
        assertThat(Arrays.asList(results.first, results.second))
                .filteredOn(value -> value != null).hasSize(1);
        assertThat(states.findById(OWNER)).get()
                .extracting(value -> value.getCurrentRevision()).isEqualTo(1L);
        assertThat(contacts.findByOwnerUserId(OWNER)).hasSize(1);
    }

    @Test
    void tenThousandContactSnapshotCommitsAndReplays() {
        String snapshotId = UUID.randomUUID().toString();
        ContactSnapshotUploadSessionV2Response session =
                create(10_000, 20, snapshotId, 0);
        for (int index = 0; index < 20; index++) {
            service.uploadChunk(
                    OWNER, session.getSessionId(), index, chunk(phones(index * 500, 500)));
        }

        long commitStarted = System.nanoTime();
        ContactSnapshotV2Response committed = service.commit(OWNER, session.getSessionId());
        long commitMillis = (System.nanoTime() - commitStarted) / 1_000_000;

        assertThat(committed.getCanonicalCount()).isEqualTo(10_000);
        assertThat(contacts.findByOwnerUserId(OWNER)).hasSize(10_000);
        assertThat(service.commit(OWNER, session.getSessionId()))
                .usingRecursiveComparison().isEqualTo(committed);
        assertThat(commitMillis).isLessThan(30_000);
    }

    @Test
    void priorLifecycleSessionsCannotUploadOrCommit() {
        ContactSnapshotUploadSessionV2Response commitCandidate =
                create(1, 1, UUID.randomUUID().toString(), 0);
        ContactSnapshotUploadSessionV2Response uploadCandidate =
                create(1, 1, UUID.randomUUID().toString(), 0);
        service.uploadChunk(
                OWNER, commitCandidate.getSessionId(), 0, chunk(phone(0)));

        com.odin.profileservice.entity.ContactSnapshotOwnerLockV2 lock =
                locks.findById(OWNER).orElseThrow();
        lock.setContactLifecycleEpoch(1L);
        locks.saveAndFlush(lock);

        assertThatThrownBy(() -> service.uploadChunk(
                OWNER, uploadCandidate.getSessionId(), 0, chunk(phone(1))))
                .isInstanceOf(ContactSnapshotV2ReplayExpiredException.class);
        assertThatThrownBy(() -> service.commit(
                OWNER, commitCandidate.getSessionId()))
                .isInstanceOf(ContactSnapshotV2ReplayExpiredException.class);
        assertThat(contacts.findByOwnerUserId(OWNER)).isEmpty();
    }

    private ContactSnapshotUploadSessionV2Response create(
            int totalContacts,
            int totalChunks,
            String snapshotId,
            long revision) {
        return service.create(OWNER,
                sessionRequest(totalContacts, totalChunks, snapshotId, revision));
    }

    private ContactSnapshotUploadSessionV2Request sessionRequest(
            int totalContacts,
            int totalChunks,
            String snapshotId,
            long revision) {
        return ContactSnapshotUploadSessionV2Request.builder()
                .snapshotId(snapshotId)
                .baseRevision(revision)
                .countryCode("IN")
                .totalContacts(totalContacts)
                .totalChunks(totalChunks)
                .build();
    }

    private ContactSnapshotUploadChunkV2Request chunk(String... values) {
        return chunk(List.of(values));
    }

    private ContactSnapshotUploadChunkV2Request chunk(List<String> values) {
        return ContactSnapshotUploadChunkV2Request.builder()
                .contacts(values.stream()
                        .map(value -> ContactSnapshotV2Request.ContactItem.builder()
                                .phoneNumber(value)
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    private List<String> phones(int start, int count) {
        return IntStream.range(start, start + count)
                .mapToObj(this::phone)
                .collect(Collectors.toList());
    }

    private String phone(int value) {
        return "+919" + String.format("%09d", value);
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
