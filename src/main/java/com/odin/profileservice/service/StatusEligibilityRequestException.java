package com.odin.profileservice.service;

import com.odin.profileservice.enums.StatusEligibilityReason;

public class StatusEligibilityRequestException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final StatusEligibilityReason reason;

    public StatusEligibilityRequestException(StatusEligibilityReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public StatusEligibilityReason getReason() { return reason; }
}
