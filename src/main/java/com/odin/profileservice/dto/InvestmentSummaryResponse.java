package com.odin.profileservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InvestmentSummaryResponse {

    private Long investmentId;

    private Long schemeId;

    private String schemeName;

    private BigDecimal principalBalance;

    private BigDecimal profitBalance;

    private BigDecimal eligibleCashback;

    private BigDecimal lockedCashback;

    private LocalDate maturityDate;

    private String status;
}