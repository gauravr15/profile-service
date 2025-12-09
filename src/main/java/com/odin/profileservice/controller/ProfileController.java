package com.odin.profileservice.controller;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.odin.profileservice.constants.ApplicationConstants;
import com.odin.profileservice.dto.MobileListDTO;
import com.odin.profileservice.dto.ProfileDTO;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.enums.CustomerType;
import com.odin.profileservice.factory.CustomerFactory;
import com.odin.profileservice.utility.ResponseObject;

@RestController
@RequestMapping(value = ApplicationConstants.API_VERSION)
public class ProfileController {

	@Autowired
	ResponseObject response;

	@Autowired
	private CustomerFactory factory;

	@PostMapping(ApplicationConstants.CUSTOMER + ApplicationConstants.DETAILS)
	public ResponseEntity<Object> customerDetails(HttpServletRequest servlet, @RequestBody ProfileDTO profileDTO) {
		ResponseDTO response = factory.getInstance(profileDTO).fetchProfileDetails(profileDTO);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping(ApplicationConstants.CUSTOMER + ApplicationConstants.DETAILS + "/{type}" + "/{mobile}")
	public ResponseEntity<Object> customerDetails(HttpServletRequest servlet, @PathVariable String type,
			@PathVariable String mobile) {
		CustomerType customerType = CustomerType.valueOf(type);
		ResponseDTO response = factory.getInstance(customerType).fetchCustomerId(type, mobile);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}
	
	@PostMapping(ApplicationConstants.BULK + ApplicationConstants.CUSTOMER + ApplicationConstants.DETAILS)
	public ResponseEntity<Object> fetchCustomerByMobile(HttpServletRequest servlet,
			@RequestBody MobileListDTO mobiles) {
		CustomerType customerType = CustomerType.CUSTOMER;
		ResponseDTO response = factory.getInstance(customerType).fetchCustomerByMobile(customerType, mobiles);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

}
