package com.odin.profileservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WithdrawalEligibilityResponse {

    private BigDecimal availablePrincipal;

    private BigDecimal availableProfit;

    private BigDecimal eligibleCashback;

    private BigDecimal lockedCashback;

    private Boolean canWithdraw;

    private Boolean withdrawalLock;

    private LocalDate nextCashbackEligibility;
}