package com.odin.profileservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WithdrawalListResponse {

    private Long requestId;

    private BigDecimal amount;

    private String status;

    private LocalDateTime requestTime;
}