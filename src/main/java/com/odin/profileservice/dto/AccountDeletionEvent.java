package com.odin.profileservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Kafka event published when a user deletes their account.
 * Consumed by downstream services to remove contact sync data for this user.
 * globalPhoneHash allows consumers to identify and purge contact records
 * where other users had synced this person's phone number.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDeletionEvent {

    private String eventId;

    private String customerId;

    private String globalPhoneHash;

    private long timestamp;

    private List<String> contactOwnerIds;
}
