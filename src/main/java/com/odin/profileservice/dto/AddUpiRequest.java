package com.odin.profileservice.dto;

import lombok.Data;

@Data
public class AddUpiRequest {

    private String upiId;

    private String accountHolderName;
}