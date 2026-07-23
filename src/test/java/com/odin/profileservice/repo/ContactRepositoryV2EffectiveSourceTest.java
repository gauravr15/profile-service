package com.odin.profileservice.repo;

import com.odin.profileservice.entity.Contact;
import com.odin.profileservice.entity.ContactSyncStateV2;
import com.odin.profileservice.entity.ContactV2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class ContactRepositoryV2EffectiveSourceTest {
    @Autowired
    private ContactRepository legacyContacts;
    @Autowired
    private ContactV2Repository v2Contacts;
    @Autowired
    private ContactSyncStateV2Repository states;

    @Test
    void legacyIsEffectiveUntilFirstV2SnapshotThenV2RemainsAuthoritative() {
        legacyContacts.save(Contact.builder()
                .ownerUserId("59")
                .targetGlobalPhoneHash("legacy-hash")
                .build());
        legacyContacts.save(Contact.builder()
                .ownerUserId("72")
                .targetGlobalPhoneHash("other-legacy-hash")
                .build());

        assertThat(legacyContacts.findByOwnerUserId("59"))
                .extracting(Contact::getTargetGlobalPhoneHash)
                .containsExactly("legacy-hash");

        v2Contacts.save(ContactV2.builder()
                .ownerUserId("59")
                .targetGlobalPhoneHash("v2-hash")
                .build());
        states.save(ContactSyncStateV2.builder()
                .ownerUserId("59")
                .currentRevision(1L)
                .lastSnapshotId(UUID.randomUUID().toString())
                .lastSyncedAt(new Timestamp(System.currentTimeMillis()))
                .build());
        legacyContacts.save(Contact.builder()
                .ownerUserId("59")
                .targetGlobalPhoneHash("late-v1-hash")
                .build());

        assertThat(legacyContacts.findByOwnerUserId("59"))
                .extracting(Contact::getTargetGlobalPhoneHash)
                .containsExactly("v2-hash");
        assertThat(legacyContacts.existsByOwnerUserIdAndTargetGlobalPhoneHash("59", "v2-hash"))
                .isTrue();
        assertThat(legacyContacts.existsByOwnerUserIdAndTargetGlobalPhoneHash("59", "legacy-hash"))
                .isFalse();
        assertThat(legacyContacts.countByOwnerUserId("59")).isOne();
        assertThat(legacyContacts.findByOwnerUserId("72"))
                .extracting(Contact::getTargetGlobalPhoneHash)
                .containsExactly("other-legacy-hash");
    }
}
