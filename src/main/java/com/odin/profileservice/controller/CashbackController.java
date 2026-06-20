package com.odin.profileservice.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.odin.profileservice.constants.ApplicationConstants;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.service.CashbackService;

@RestController
@RequestMapping(ApplicationConstants.API_VERSION)
public class CashbackController {

    @Autowired
    private CashbackService cashbackService;

    @GetMapping("/cashback/history")
    public ResponseEntity<Object> cashbackHistory(
            @RequestHeader("customerId") String customerId) {

        ResponseDTO dto =
                cashbackService.getHistory(
                        Integer.valueOf(customerId));

        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @GetMapping("/cashback/schedules")
    public ResponseEntity<Object> cashbackSchedules(
            @RequestHeader("customerId") String customerId) {

        ResponseDTO dto =
                cashbackService.getSchedules(
                        Integer.valueOf(customerId));

        return new ResponseEntity<>(dto, HttpStatus.OK);
    }
}