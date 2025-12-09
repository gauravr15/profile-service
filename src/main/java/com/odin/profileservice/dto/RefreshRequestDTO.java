package com.odin.profileservice.dto;

import lombok.Data;

@Data
public class RefreshRequestDTO {
    private String refreshToken;
    private String deviceSignature; // required for per-device binding
}
