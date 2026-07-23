package com.odin.profileservice.utility;

import com.odin.profileservice.dto.AccountDeletionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

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

    @Value("${app.account-deletion.kafka-ack-timeout-seconds:10}")
    private long acknowledgmentTimeoutSeconds;

    public void publishAcknowledged(AccountDeletionEvent event) throws Exception {
        log.info("Publishing account deletion event topic={} eventId={}",
                TOPIC, event.getEventId());
        accountDeletionKafkaTemplate
                .send(TOPIC, event.getEventId(), event)
                .get(acknowledgmentTimeoutSeconds, TimeUnit.SECONDS);
    }
}
