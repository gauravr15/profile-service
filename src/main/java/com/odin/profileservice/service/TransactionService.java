package com.odin.profileservice.service;

import com.odin.profileservice.dto.ArchiveStatementRequest;
import com.odin.profileservice.dto.ResponseDTO;

public interface TransactionService {

	ResponseDTO getTransactions(Integer customerId, Integer page, Integer size, String fromDate, String toDate);

	ResponseDTO sendArchiveStatement(Integer customerId, ArchiveStatementRequest request);
}