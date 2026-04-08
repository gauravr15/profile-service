package com.odin.profileservice.utility;

import com.odin.profileservice.dto.GroupCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes group lifecycle events to Kafka so downstream services
 * (e.g. web-socket-service) can notify members in real time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupEventProducer {

    private static final String TOPIC = "group-events";

    private final KafkaTemplate<String, GroupCreatedEvent> groupEventKafkaTemplate;

    public void publishGroupCreated(GroupCreatedEvent event) {
        log.info("[GROUP-EVENT] Publishing GroupCreatedEvent groupId={} name={} members={}",
                event.getGroupId(), event.getGroupName(),
                event.getMemberIds() != null ? event.getMemberIds().size() : 0);
        groupEventKafkaTemplate.send(TOPIC, event.getGroupId(), event);
    }
}
