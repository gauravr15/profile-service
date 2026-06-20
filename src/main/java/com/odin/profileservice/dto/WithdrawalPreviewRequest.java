package com.odin.profileservice.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class WithdrawalPreviewRequest {

    private Long investmentId;

    private BigDecimal principalWithdrawal;

    private BigDecimal profitWithdrawal;

    private BigDecimal cashbackWithdrawal;
}