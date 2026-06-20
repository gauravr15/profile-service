package com.odin.profileservice.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WithdrawalLockResponse {

    private Boolean locked;

    private LocalDateTime unlockAt;

    private String reason;
}