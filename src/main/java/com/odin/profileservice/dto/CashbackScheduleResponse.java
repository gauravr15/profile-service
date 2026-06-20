package com.odin.profileservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class CashbackScheduleResponse {

    private BigDecimal amount;

    private LocalDateTime eligibleAt;

    private String status;
}