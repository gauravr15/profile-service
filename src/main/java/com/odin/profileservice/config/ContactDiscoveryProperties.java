package com.odin.profileservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.contact.discovery")
public class ContactDiscoveryProperties {
    private int maxNumbers = 5_000;
    private int targetBatchSize = 200;
    private int maxPhoneLength = 32;
    private int bulkRequestsPerMinute = 10;
    private int singleRequestsPerMinute = 30;
    private int dailyUniqueNumbers = 10_000;
    private int temporaryBlockSeconds = 900;
    private int abuseMinimumRequests = 10;
    private int abuseUnmatchedPercent = 95;
    private String environment = "default";

    @PostConstruct
    void validate() {
        if (maxNumbers < 1 || targetBatchSize < 1 || targetBatchSize > maxNumbers
                || maxPhoneLength < 8 || maxPhoneLength > 128
                || bulkRequestsPerMinute < 1 || singleRequestsPerMinute < 1
                || dailyUniqueNumbers < maxNumbers || temporaryBlockSeconds < 1
                || abuseMinimumRequests < 1
                || abuseUnmatchedPercent < 1 || abuseUnmatchedPercent > 100
                || environment == null || environment.trim().isEmpty()) {
            throw new IllegalStateException("Invalid contact discovery configuration");
        }
    }
}
