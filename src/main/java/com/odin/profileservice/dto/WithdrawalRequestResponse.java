package com.odin.profileservice.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WithdrawalRequestResponse {

    private Long withdrawalRequestId;

    private BigDecimal requestedAmount;

    private String status;
}