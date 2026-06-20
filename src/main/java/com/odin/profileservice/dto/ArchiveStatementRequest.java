package com.odin.profileservice.dto;

import lombok.Data;

@Data
public class ArchiveStatementRequest {

    private String fromDate;

    private String toDate;
}