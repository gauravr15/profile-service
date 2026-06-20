package com.odin.profileservice.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.odin.profileservice.constants.ApplicationConstants;
import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.service.impl.DashboardServiceImpl;
import com.odin.profileservice.utility.ResponseObject;

@RestController
@RequestMapping(ApplicationConstants.API_VERSION)
public class DashboardController {

    @Autowired
    private DashboardServiceImpl dashboardService;

    @Autowired
    private ResponseObject response;

    @GetMapping("/dashboard/summary")
    public ResponseEntity<Object> getDashboardSummary(
            @RequestHeader("customerId") String customerId) {

        if (ObjectUtils.isEmpty(customerId)) {
            ResponseDTO dto =
                    response.buildResponse(ApplicationConstants.APP_LANG,
                            ResponseCodes.INVALID_REQUEST);
            return new ResponseEntity<>(dto, HttpStatus.OK);
        }

        ResponseDTO dto =
                dashboardService.getDashboardSummary(Integer.valueOf(customerId));

        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @GetMapping("/dashboard/investments")
    public ResponseEntity<Object> getInvestments(
            @RequestHeader("customerId") String customerId) {

        ResponseDTO dto =
                dashboardService.getInvestments(Integer.valueOf(customerId));

        return new ResponseEntity<>(dto, HttpStatus.OK);
    }
}