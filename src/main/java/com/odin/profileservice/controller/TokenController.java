package com.odin.profileservice.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.odin.profileservice.constants.ApplicationConstants;
import com.odin.profileservice.dto.RefreshRequestDTO;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.service.TokenService;

@RestController
@RequestMapping(value = ApplicationConstants.API_VERSION)
public class TokenController {

	
	@Autowired
	private TokenService tokenService;

	@PostMapping("/token/refresh")
	public ResponseEntity<ResponseDTO> refresh(@RequestBody RefreshRequestDTO request) {
		ResponseDTO response = tokenService.refresh(request);
		return new ResponseEntity<>(response, HttpStatus.OK); 
	}
	
	@PostMapping("/token/revoke")
	public ResponseEntity<ResponseDTO> revokeToken(@RequestBody RefreshRequestDTO request) {
		ResponseDTO response = tokenService.revoke(request);
		return new ResponseEntity<>(response, HttpStatus.OK); 
	}
}
