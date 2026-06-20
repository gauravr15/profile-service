package com.odin.profileservice.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class WithdrawalRequestDto {

    private Long investmentId;

    private BigDecimal principalAmount;

    private BigDecimal profitAmount;

    private BigDecimal cashbackAmount;
}