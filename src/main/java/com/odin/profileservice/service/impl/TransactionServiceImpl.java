package com.odin.profileservice.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.ArchiveStatementRequest;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.dto.TransactionPageResponse;
import com.odin.profileservice.dto.TransactionResponse;
import com.odin.profileservice.entity.InvestmentLedger;
import com.odin.profileservice.repo.InvestmentLedgerRepository;
import com.odin.profileservice.service.TransactionService;
import com.odin.profileservice.utility.ResponseObject;

@Service
public class TransactionServiceImpl
        implements TransactionService {

    @Autowired
    private InvestmentLedgerRepository ledgerRepo;

    @Autowired
    private ResponseObject response;

    @Override
    public ResponseDTO getTransactions(
            Integer customerId,
            Integer page,
            Integer size,
            String fromDate,
            String toDate) {

        LocalDateTime from =
                LocalDate.parse(fromDate)
                        .atStartOfDay();

        LocalDateTime to =
                LocalDate.parse(toDate)
                        .plusDays(1)
                        .atStartOfDay();

        Page<InvestmentLedger> ledgerPage =
                ledgerRepo
                        .findByCustomerIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                                customerId,
                                from,
                                to,
                                PageRequest.of(page, size));

        TransactionPageResponse dto =
                TransactionPageResponse.builder()
                        .content(
                                ledgerPage.getContent()
                                        .stream()
                                        .map(this::mapToResponse)
                                        .collect(Collectors.toList()))
                        .totalElements(
                                ledgerPage.getTotalElements())
                        .totalPages(
                                ledgerPage.getTotalPages())
                        .build();

        return response.buildResponse(
                ResponseCodes.SUCCESS_CODE,
                dto);
    }

    private TransactionResponse mapToResponse(
            InvestmentLedger ledger) {

        return TransactionResponse.builder()
                .txnId(ledger.getId())
                .txnType(ledger.getTxnType())
                .amount(ledger.getAmount())
                .createdAt(ledger.getCreatedAt())
                .build();
    }

    @Override
    public ResponseDTO sendArchiveStatement(
            Integer customerId,
            ArchiveStatementRequest request) {

        /*
         * Future flow:
         *
         * 1. Read archived ledger table
         * 2. Generate CSV/PDF
         * 3. Upload file
         * 4. Email customer
         * 5. Audit log
         */

        return response.buildResponse(
                ResponseCodes.SUCCESS_CODE,
                "Transaction statement will be emailed shortly");
    }
}