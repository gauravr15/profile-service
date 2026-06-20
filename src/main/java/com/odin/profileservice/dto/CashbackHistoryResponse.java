package com.odin.profileservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class CashbackHistoryResponse {

    private BigDecimal cashbackAmount;

    private LocalDateTime creditedAt;

    private Long investmentId;
}