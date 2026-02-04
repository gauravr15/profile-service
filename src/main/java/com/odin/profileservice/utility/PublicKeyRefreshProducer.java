package com.odin.profileservice.utility;

import com.odin.profileservice.constants.ApplicationConstants;
import com.odin.profileservice.dto.PublicKeyRefreshEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Produces Kafka messages to trigger public key refresh operations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublicKeyRefreshProducer {

    private static final String TOPIC = "refresh.public.key";

    private final KafkaTemplate<String, PublicKeyRefreshEvent> publicKeyRefreshKafkaTemplate;

    public void publish(String customerId) {
        PublicKeyRefreshEvent event = PublicKeyRefreshEvent.builder()
                .customerId(customerId)
                .action(ApplicationConstants.FETCH_PUBLIC_KEY)
                .build();

        log.info("Sending public key refresh event for customerId={} to topic={} action={}",
                customerId, TOPIC, event.getAction());
        publicKeyRefreshKafkaTemplate.send(TOPIC, event);
    }
}
