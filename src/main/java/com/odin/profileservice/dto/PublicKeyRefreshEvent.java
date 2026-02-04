package com.odin.profileservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event payload for requesting a public key refresh via Kafka.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicKeyRefreshEvent {

    private String customerId;
    private String action;
}
