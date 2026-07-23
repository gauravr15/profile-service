package com.odin.profileservice.service;

import com.odin.profileservice.entity.AccountDeletionOutbox;
import com.odin.profileservice.repo.AccountDeletionOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(AccountDeletionOutboxClaimService.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class AccountDeletionOutboxClaimServiceTest {

    @Autowired
    private AccountDeletionOutboxClaimService service;
    @Autowired
    private AccountDeletionOutboxRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.save(pending("event-a"));
    }

    @Test
    void competingWorkersCannotBothClaimSameEvent() {
        Instant now = Instant.now();

        assertThat(service.claim("event-a", "worker-a", now, 60)).isPresent();
        assertThat(service.claim("event-a", "worker-b", now, 60)).isEmpty();
        assertThat(repository.findById("event-a").orElseThrow().getAttemptCount())
                .isOne();
    }

    @Test
    void expiredClaimIsRecoveredAfterRestart() {
        Instant now = Instant.now();
        assertThat(service.claim("event-a", "old-process", now, 1)).isPresent();

        assertThat(service.claim("event-a", "new-process", now.plusSeconds(2), 60))
                .isPresent();
        assertThat(repository.findById("event-a").orElseThrow().getClaimToken())
                .isEqualTo("new-process");
    }

    private AccountDeletionOutbox pending(String eventId) {
        Timestamp now = Timestamp.from(Instant.now().minusSeconds(1));
        return AccountDeletionOutbox.builder()
                .eventId(eventId)
                .aggregateType("ACCOUNT")
                .aggregateId("59:" + eventId)
                .eventType("ACCOUNT_DELETED")
                .payload("{}")
                .status(AccountDeletionOutbox.Status.PENDING)
                .attemptCount(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .build();
    }
}
