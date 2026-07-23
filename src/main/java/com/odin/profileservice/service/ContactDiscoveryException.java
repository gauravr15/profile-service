package com.odin.profileservice.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ContactDiscoveryException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final int retryAfterSeconds;

    private ContactDiscoveryException(
            HttpStatus status, String code, int retryAfterSeconds) {
        super(code);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static ContactDiscoveryException invalidRequest() {
        return new ContactDiscoveryException(
                HttpStatus.BAD_REQUEST, "CONTACT_LOOKUP_INVALID_REQUEST", 0);
    }

    public static ContactDiscoveryException rateLimited(int retryAfterSeconds) {
        return new ContactDiscoveryException(
                HttpStatus.TOO_MANY_REQUESTS, "CONTACT_LOOKUP_RATE_LIMITED",
                retryAfterSeconds);
    }

    public static ContactDiscoveryException unavailable() {
        return new ContactDiscoveryException(
                HttpStatus.SERVICE_UNAVAILABLE, "CONTACT_LOOKUP_UNAVAILABLE", 5);
    }
}
