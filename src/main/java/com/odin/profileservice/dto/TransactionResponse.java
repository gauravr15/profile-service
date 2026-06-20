package com.odin.profileservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransactionResponse {

    private Long txnId;

    private String txnType;

    private BigDecimal amount;

    private LocalDateTime createdAt;
}