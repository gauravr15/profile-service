package com.odin.profileservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Kafka event published when a new group is created.
 * Consumed by web-socket-service to notify members in real time.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupCreatedEvent {

    private String groupId;
    private String groupName;
    private List<String> memberIds;
    private String creatorId;
    private long createdAt;
}
