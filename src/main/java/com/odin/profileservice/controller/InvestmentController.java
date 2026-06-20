package com.odin.profileservice.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.odin.profileservice.constants.ApplicationConstants;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.service.InvestmentService;

@RestController
@RequestMapping(ApplicationConstants.API_VERSION)
public class InvestmentController {

    @Autowired
    private InvestmentService investmentService;

    @GetMapping("/investments/{investmentId}")
    public ResponseEntity<Object> getInvestmentDetails(
            @PathVariable Long investmentId,
            @RequestHeader("customerId") String customerId) {

        ResponseDTO dto =
                investmentService.getInvestmentDetails(
                        Integer.valueOf(customerId),
                        investmentId);

        return new ResponseEntity<>(dto, HttpStatus.OK);
    }
}