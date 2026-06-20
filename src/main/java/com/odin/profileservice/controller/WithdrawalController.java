package com.odin.profileservice.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.odin.profileservice.constants.ApplicationConstants;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.dto.WithdrawalPreviewRequest;
import com.odin.profileservice.dto.WithdrawalRequestDto;
import com.odin.profileservice.service.WithdrawalService;

@RestController
@RequestMapping(ApplicationConstants.API_VERSION)
public class WithdrawalController {

    @Autowired
    private WithdrawalService withdrawalService;

    @PostMapping("/withdrawal/preview")
    public ResponseEntity<Object> previewWithdrawal(
            @RequestBody WithdrawalPreviewRequest request,
            @RequestHeader("customerId") String customerId) {

        ResponseDTO dto =
                withdrawalService.previewWithdrawal(
                        Integer.valueOf(customerId),
                        request);

        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @PostMapping("/withdrawal/request")
    public ResponseEntity<Object> createWithdrawalRequest(
            @RequestBody WithdrawalRequestDto request,
            @RequestHeader("customerId") String customerId) {

        ResponseDTO dto =
                withdrawalService.createWithdrawalRequest(
                        Integer.valueOf(customerId),
                        request);

        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @GetMapping("/withdrawal/requests")
    public ResponseEntity<Object> getRequests(
            @RequestHeader("customerId") String customerId) {

        ResponseDTO dto =
                withdrawalService.getRequests(
                        Integer.valueOf(customerId));

        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @PostMapping("/withdrawal/{id}/cancel")
    public ResponseEntity<Object> cancelWithdrawal(
            @PathVariable Long id,
            @RequestHeader("customerId") String customerId) {

        ResponseDTO dto =
                withdrawalService.cancelWithdrawal(
                        Integer.valueOf(customerId),
                        id);

        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @GetMapping("/withdrawal/lock-status")
    public ResponseEntity<Object> lockStatus() {

        ResponseDTO dto =
                withdrawalService.getLockStatus();

        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @GetMapping("/withdrawal/eligibility/{investmentId}")
    public ResponseEntity<Object> getEligibility(
            @PathVariable Long investmentId,
            @RequestHeader("customerId") String customerId) {

        ResponseDTO dto =
                withdrawalService.getEligibility(
                        Integer.valueOf(customerId),
                        investmentId);

        return new ResponseEntity<>(dto, HttpStatus.OK);
    }
}