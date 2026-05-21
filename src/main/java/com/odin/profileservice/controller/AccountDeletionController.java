package com.odin.profileservice.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.odin.profileservice.constants.ApplicationConstants;
import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.service.AccountDeletionService;
import com.odin.profileservice.utility.ResponseObject;

@RestController
@RequestMapping(value = ApplicationConstants.API_VERSION)
public class AccountDeletionController {

    @Autowired
    private AccountDeletionService accountDeletionService;

    @Autowired
    private ResponseObject response;

    @PostMapping(ApplicationConstants.CUSTOMER + ApplicationConstants.DELETE)
    public ResponseEntity<Object> deleteAccount(
            @RequestHeader(value = "customerId", required = true) String customerId) {

        if (ObjectUtils.isEmpty(customerId)) {
            ResponseDTO responseDto = response.buildResponse(ApplicationConstants.APP_LANG, ResponseCodes.INVALID_REQUEST);
            return new ResponseEntity<>(responseDto, HttpStatus.OK);
        }

        ResponseDTO responseDto = accountDeletionService.deleteAccount(customerId);
        return new ResponseEntity<>(responseDto, HttpStatus.OK);
    }
}
