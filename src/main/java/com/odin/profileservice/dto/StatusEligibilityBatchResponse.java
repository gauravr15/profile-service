package com.odin.profileservice.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StatusEligibilityBatchResponse {
    private final String viewerId;
    private final List<StatusEligibilityResult> results;
}
