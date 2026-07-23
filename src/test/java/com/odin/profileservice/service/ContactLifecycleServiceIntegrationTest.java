package com.odin.profileservice.service;

import com.odin.profileservice.entity.Contact;
import com.odin.profileservice.entity.ContactSnapshotOwnerLockV2;
import com.odin.profileservice.entity.ContactSnapshotRequestV2;
import com.odin.profileservice.entity.ContactSnapshotRequestV2Id;
import com.odin.profileservice.entity.ContactSnapshotUploadSessionV2;
import com.odin.profileservice.entity.ContactSyncStateV2;
import com.odin.profileservice.entity.ContactV2;
import com.odin.profileservice.repo.ContactRepository;
import com.odin.profileservice.repo.ContactSnapshotOwnerLockV2Repository;
import com.odin.profileservice.repo.ContactSnapshotRequestV2Repository;
import com.odin.profileservice.repo.ContactSnapshotUploadSessionV2Repository;
import com.odin.profileservice.repo.ContactSyncStateV2Repository;
import com.odin.profileservice.repo.ContactV2Repository;
import com.odin.profileservice.repo.SyncAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@DataJpaTest
@Import(ContactLifecycleService.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class ContactLifecycleServiceIntegrationTest {
    private static final String OWNER = "59";
    private static final String TARGET = "target-hash";

    @Autowired
    private ContactLifecycleService service;
    @Autowired
    private ContactRepository contacts;
    @Autowired
    private ContactV2Repository contactsV2;
    @Autowired
    private ContactSyncStateV2Repository states;
    @Autowired
    private ContactSnapshotRequestV2Repository requests;
    @Autowired
    private ContactSnapshotOwnerLockV2Repository locks;
    @Autowired
    private ContactSnapshotUploadSessionV2Repository sessions;
    @Autowired
    private SyncAuditRepository audits;

    @MockBean
    private ContactDiscoveryRateLimiter discoveryRateLimiter;

    @BeforeEach
    void setUp() {
        sessions.deleteAll();
        requests.deleteAll();
        states.deleteAll();
        contactsV2.deleteAll();
        contacts.deleteAll();
        locks.deleteAll();
        locks.save(lock(OWNER));
    }

    @Test
    void explicitDeletionRemovesOwnerStateAndTombstonesReplay() {
        contacts.save(contact(OWNER, TARGET));
        contacts.save(contact("63", "unrelated"));
        contactsV2.save(contactV2(OWNER, TARGET));
        states.save(ContactSyncStateV2.builder()
                .ownerUserId(OWNER).currentRevision(1L)
                .lastSnapshotId(UUID.randomUUID().toString())
                .lastSyncedAt(now()).build());
        String snapshotId = UUID.randomUUID().toString();
        requests.save(request(OWNER, snapshotId));
        sessions.save(session(OWNER));

        service.deleteSyncedContacts(OWNER);
        service.deleteSyncedContacts(OWNER);

        assertThat(contacts.findAll())
                .extracting(Contact::getOwnerUserId)
                .containsExactly("63");
        assertThat(contactsV2.findByOwnerUserId(OWNER)).isEmpty();
        assertThat(states.findById(OWNER)).isEmpty();
        assertThat(sessions.findAll()).isEmpty();
        ContactSnapshotRequestV2 tombstone = requests.findById(
                new ContactSnapshotRequestV2Id(OWNER, snapshotId)).orElseThrow();
        assertThat(tombstone.getStatus()).isEqualTo(ContactSnapshotRequestV2.Status.TOMBSTONE);
        assertThat(tombstone.getCanonicalCount()).isNull();
        assertThat(locks.findById(OWNER).orElseThrow().getContactLifecycleEpoch())
                .isEqualTo(2L);
    }

    @Test
    void accountDeletionRemovesForwardAndReverseRowsOnlyForDeletedIdentity() {
        contacts.save(contact(OWNER, "owned"));
        contacts.save(contact("63", TARGET));
        contacts.save(contact("72", "unrelated"));
        contactsV2.save(contactV2("74", TARGET));
        contactsV2.save(contactV2("75", "unrelated-v2"));

        service.deleteAccountContacts(OWNER, TARGET);

        assertThat(contacts.findAll())
                .extracting(Contact::getTargetGlobalPhoneHash)
                .containsExactly("unrelated");
        assertThat(contactsV2.findAll())
                .extracting(ContactV2::getTargetGlobalPhoneHash)
                .containsExactly("unrelated-v2");
    }

    @Test
    void discoveryCleanupUsesOnlyDerivedAccountStateOperation() {
        service.clearTemporaryDiscoveryState(OWNER);
        verify(discoveryRateLimiter).clearAccountState(OWNER);
    }

    private ContactSnapshotOwnerLockV2 lock(String owner) {
        return ContactSnapshotOwnerLockV2.builder()
                .ownerUserId(owner).createdAt(now()).contactLifecycleEpoch(0L).build();
    }

    private Contact contact(String owner, String target) {
        return Contact.builder().ownerUserId(owner).targetGlobalPhoneHash(target).build();
    }

    private ContactV2 contactV2(String owner, String target) {
        return ContactV2.builder().ownerUserId(owner).targetGlobalPhoneHash(target).build();
    }

    private ContactSnapshotRequestV2 request(String owner, String snapshotId) {
        return ContactSnapshotRequestV2.builder()
                .ownerUserId(owner).snapshotId(snapshotId).payloadDigest("digest")
                .baseRevision(0L).committedRevision(1L)
                .status(ContactSnapshotRequestV2.Status.COMMITTED)
                .canonicalCount(1).createdAt(now()).updatedAt(now())
                .contactLifecycleEpoch(0L).build();
    }

    private ContactSnapshotUploadSessionV2 session(String owner) {
        return ContactSnapshotUploadSessionV2.builder()
                .sessionId(UUID.randomUUID().toString()).ownerUserId(owner)
                .snapshotId(UUID.randomUUID().toString()).baseRevision(0L)
                .countryCode("IN").declaredTotalContacts(1).declaredTotalChunks(1)
                .state(ContactSnapshotUploadSessionV2.State.OPEN)
                .expiresAt(Timestamp.from(Instant.now().plusSeconds(3600)))
                .createdAt(now()).updatedAt(now()).contactLifecycleEpoch(0L).build();
    }

    private Timestamp now() {
        return Timestamp.from(Instant.now());
    }
}
