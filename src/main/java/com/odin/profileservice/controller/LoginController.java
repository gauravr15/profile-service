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
import com.odin.profileservice.dto.ProfileDTO;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.factory.CustomerFactory;
import com.odin.profileservice.utility.ResponseObject;


@RestController
@RequestMapping(value = ApplicationConstants.API_VERSION)
public class LoginController {
	
	@Autowired
	ResponseObject response;
	
	@Autowired
	private CustomerFactory factory;
	
	@PostMapping(ApplicationConstants.SIGN_IN)
	public ResponseEntity<Object> createCustomer(HttpServletRequest servlet, @RequestBody ProfileDTO profileDTO ){
		ResponseDTO response = factory.getInstance(profileDTO).fetch(profileDTO);
		return new ResponseEntity<>(response, HttpStatus.OK); 
	}

}
