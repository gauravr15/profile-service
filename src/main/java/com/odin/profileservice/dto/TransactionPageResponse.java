package com.odin.profileservice.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransactionPageResponse {

    private List<TransactionResponse> content;

    private Long totalElements;

    private Integer totalPages;
}