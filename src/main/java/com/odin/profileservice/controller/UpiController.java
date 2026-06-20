package com.odin.profileservice.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.odin.profileservice.constants.ApplicationConstants;
import com.odin.profileservice.dto.AddUpiRequest;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.service.UpiService;

@RestController
@RequestMapping(ApplicationConstants.API_VERSION)
public class UpiController {

    @Autowired
    private UpiService upiService;

    @GetMapping("/upi")
    public ResponseEntity<Object> getUpis(
            @RequestHeader("customerId") String customerId) {

        ResponseDTO dto =
                upiService.getUpis(
                        Integer.valueOf(customerId));

        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @PostMapping("/upi")
    public ResponseEntity<Object> addUpi(
            @RequestHeader("customerId") String customerId,
            @RequestBody AddUpiRequest request) {

        ResponseDTO dto =
                upiService.addUpi(
                        Integer.valueOf(customerId),
                        request);

        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @PutMapping("/upi/{id}/primary")
    public ResponseEntity<Object> setPrimary(
            @RequestHeader("customerId") String customerId,
            @PathVariable Long id) {

        ResponseDTO dto =
                upiService.makePrimary(
                        Integer.valueOf(customerId),
                        id);

        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @DeleteMapping("/upi/{id}")
    public ResponseEntity<Object> deleteUpi(
            @RequestHeader("customerId") String customerId,
            @PathVariable Long id) {

        ResponseDTO dto =
                upiService.deleteUpi(
                        Integer.valueOf(customerId),
                        id);

        return new ResponseEntity<>(dto, HttpStatus.OK);
    }
}