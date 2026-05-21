package com.odin.profileservice.utility;

import com.odin.profileservice.dto.AccountDeletionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Produces Kafka messages to trigger downstream cleanup when a user deletes their account.
 * Consumers (e.g. contact-sync-cleaner) should purge contact entries where
 * targetGlobalPhoneHash matches the deleted user's globalPhoneHash.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountDeletionProducer {

    private static final String TOPIC = "account.deletion";

    private final KafkaTemplate<String, AccountDeletionEvent> accountDeletionKafkaTemplate;

    public void publish(AccountDeletionEvent event) {
        log.info("[DELETE-ACCOUNT] Publishing deletion event to topic={} for customerId={}",
                TOPIC, event.getCustomerId());
        accountDeletionKafkaTemplate.send(TOPIC, event);
    }
}
