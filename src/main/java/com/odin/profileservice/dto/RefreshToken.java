package com.odin.profileservice.dto;

import java.sql.Timestamp;

public class RefreshToken {
    private Long id;
    private Long customerId;
    private String refreshToken;
    private String deviceSignature;
    private Timestamp expiryDate;
}
