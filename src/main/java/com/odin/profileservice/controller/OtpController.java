package com.odin.profileservice.controller;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.odin.profileservice.constants.ApplicationConstants;
import com.odin.profileservice.dto.OtpRequestDTO;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.service.OTPGenerationService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping(ApplicationConstants.API_VERSION)
public class OtpController {
	
	@Autowired
	private OTPGenerationService service;

	@PostMapping(ApplicationConstants.GENERATE_OTP)
	public ResponseEntity<Object> generateOTP(HttpServletRequest req, @RequestBody OtpRequestDTO dto){
		log.info("Inside generate otp controller for : {}",dto);
		ResponseDTO resp = service.generateOtp(req, dto);
		return new ResponseEntity<>(resp, HttpStatus.OK);
	}
}
