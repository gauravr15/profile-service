package com.odin.profileservice.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardSummaryResponse {

    private BigDecimal totalPrincipal;

    private BigDecimal totalProfit;

    private BigDecimal eligibleCashback;

    private BigDecimal lockedCashback;

    private Long activeInvestments;

    private Long pendingWithdrawals;
}