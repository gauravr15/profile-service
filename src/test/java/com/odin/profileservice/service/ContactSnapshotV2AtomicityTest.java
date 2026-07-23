package com.odin.profileservice.service;

import com.odin.profileservice.dto.ContactSnapshotV2Request;
import com.odin.profileservice.entity.ContactSyncStateV2;
import com.odin.profileservice.entity.ContactV2;
import com.odin.profileservice.entity.Profile;
import com.odin.profileservice.entity.User;
import com.odin.profileservice.repo.ContactSyncStateV2Repository;
import com.odin.profileservice.repo.ContactV2Repository;
import com.odin.profileservice.repo.ContactSnapshotRequestV2Repository;
import com.odin.profileservice.repo.ProfileRepository;
import com.odin.profileservice.repo.UserRepository;
import com.odin.profileservice.config.ContactTokenProperties;
import com.odin.profileservice.utility.PhoneNumberHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
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
class ContactSnapshotV2AtomicityTest {
    @Autowired
    private ContactSnapshotV2Service service;
    @Autowired
    private ContactV2Repository contacts;
    @Autowired
    private ContactSyncStateV2Repository states;
    @Autowired
    private ContactSnapshotRequestV2Repository requests;
    @Autowired
    private TransactionTemplate transactions;

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

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void downstreamAuditFailureRollsBackAddsRemovalsAndRevision() {
        transactions.executeWithoutResult(status -> {
            contacts.save(ContactV2.builder()
                    .ownerUserId("59")
                    .targetGlobalPhoneHash("previous-hash")
                    .build());
            states.save(ContactSyncStateV2.builder()
                    .ownerUserId("59")
                    .currentRevision(1L)
                    .lastSnapshotId(UUID.randomUUID().toString())
                    .lastSyncedAt(new Timestamp(System.currentTimeMillis()))
                    .build());
        });
        when(profiles.findByCustomerId(59)).thenReturn(Profile.builder()
                .customerId(59).mobile("919900000059").build());
        when(profiles.findLikeMobileNumber(anyList(), org.mockito.ArgumentMatchers.eq(true)))
                .thenReturn(Collections.emptyList());
        when(users.findById("59")).thenReturn(Optional.of(User.builder().userId("59").build()));
        doThrow(new IllegalStateException("synthetic audit failure"))
                .when(audit).updateLastSyncedTime("59");
        ContactSnapshotV2Request request = ContactSnapshotV2Request.builder()
                .snapshotId(UUID.randomUUID().toString())
                .baseRevision(1L)
                .countryCode("IN")
                .contacts(Collections.singletonList(ContactSnapshotV2Request.ContactItem.builder()
                        .phoneNumber("+919876543210")
                        .build()))
                .build();

        assertThatThrownBy(() -> service.replaceSnapshot("59", request))
                .isInstanceOf(IllegalStateException.class);

        assertThat(contacts.findByOwnerUserId("59"))
                .extracting(ContactV2::getTargetGlobalPhoneHash)
                .containsExactly("previous-hash");
        assertThat(states.findById("59")).get()
                .extracting(ContactSyncStateV2::getCurrentRevision)
                .isEqualTo(1L);
        assertThat(requests.findAll()).isEmpty();
    }
}
