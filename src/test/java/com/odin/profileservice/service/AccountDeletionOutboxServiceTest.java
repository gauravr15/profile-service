package com.odin.profileservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.profileservice.dto.AccountDeletionEvent;
import com.odin.profileservice.entity.AccountDeletionOutbox;
import com.odin.profileservice.repo.AccountDeletionOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({AccountDeletionOutboxService.class,
        AccountDeletionOutboxServiceTest.JsonConfiguration.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class AccountDeletionOutboxServiceTest {

    @Autowired
    private AccountDeletionOutboxService service;
    @Autowired
    private AccountDeletionOutboxRepository repository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void enqueueIsStableIdempotentAndContainsNoPhoneMaterial() {
        AccountDeletionEvent first = event();
        AccountDeletionEvent retry = event();

        String firstId = service.enqueue(first);
        String retryId = service.enqueue(retry);

        assertThat(retryId).isEqualTo(firstId);
        assertThat(repository.count()).isOne();
        AccountDeletionOutbox stored = repository.findById(firstId).orElseThrow();
        assertThat(stored.getPayload())
                .contains("\"eventId\":\"" + firstId + "\"")
                .contains("\"customerId\":\"59\"")
                .doesNotContain("global-hash")
                .doesNotContain("+9199");
        assertThat(stored.getStatus()).isEqualTo(AccountDeletionOutbox.Status.PENDING);
        assertThat(stored.getAttemptCount()).isZero();
    }

    @Test
    void transactionRollbackLeavesNoOutboxRow() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            transaction.executeWithoutResult(status -> {
                service.enqueue(event());
                throw new RollbackSignal();
            });
        } catch (RollbackSignal expected) {
            // rollback is the assertion subject
        }

        assertThat(repository.count()).isZero();
    }

    private AccountDeletionEvent event() {
        return AccountDeletionEvent.builder()
                .customerId("59")
                .globalPhoneHash("global-hash")
                .timestamp(100L)
                .contactOwnerIds(List.of("70"))
                .build();
    }

    static class JsonConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private static final class RollbackSignal extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
