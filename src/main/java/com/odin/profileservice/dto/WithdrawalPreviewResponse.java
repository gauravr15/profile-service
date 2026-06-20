package com.odin.profileservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WithdrawalPreviewResponse {

    private BigDecimal withdrawAmount;

    private BigDecimal remainingPrincipal;

    private BigDecimal cashbackPercentage;

    private BigDecimal cashbackToBeCredited;

    private LocalDateTime cashbackEligibleAt;
}