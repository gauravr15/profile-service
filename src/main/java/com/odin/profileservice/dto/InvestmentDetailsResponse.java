package com.odin.profileservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.odin.profileservice.entity.InvestmentScheme;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InvestmentDetailsResponse {

    private Long investmentId;

    private InvestmentScheme schemeId;

    private String schemeName;

    private BigDecimal principalBalance;

    private BigDecimal profitBalance;

    private BigDecimal eligibleCashback;

    private BigDecimal lockedCashback;

    private LocalDateTime investedAt;

    private LocalDateTime maturityAt;

    private String status;
}