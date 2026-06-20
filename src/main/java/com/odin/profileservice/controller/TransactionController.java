package com.odin.profileservice.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.odin.profileservice.constants.ApplicationConstants;
import com.odin.profileservice.dto.ArchiveStatementRequest;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.service.TransactionService;

@RestController
@RequestMapping(ApplicationConstants.API_VERSION)
public class TransactionController {

    @Autowired
    private TransactionService transactionService;

    @GetMapping("/transactions")
    public ResponseEntity<Object> getTransactions(
            @RequestHeader("customerId") String customerId,
            @RequestParam Integer page,
            @RequestParam Integer size,
            @RequestParam String fromDate,
            @RequestParam String toDate) {

        ResponseDTO dto =
                transactionService.getTransactions(
                        Integer.valueOf(customerId),
                        page,
                        size,
                        fromDate,
                        toDate);

        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @PostMapping("/transactions/archive-email")
    public ResponseEntity<Object> archiveEmail(
            @RequestHeader("customerId") String customerId,
            @RequestBody ArchiveStatementRequest request) {

        ResponseDTO dto =
                transactionService.sendArchiveStatement(
                        Integer.valueOf(customerId),
                        request);

        return new ResponseEntity<>(dto, HttpStatus.OK);
    }
}