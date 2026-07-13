package com.odin.profileservice.dto;

import com.odin.profileservice.enums.StatusEligibilityDecision;
import com.odin.profileservice.enums.StatusEligibilityReason;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StatusEligibilityResult {
    private final String uploaderId;
    private final StatusEligibilityDecision decision;
    private final StatusEligibilityReason reason;
}
