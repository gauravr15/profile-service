package com.odin.profileservice.controller;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.AuthDTO;
import com.odin.profileservice.dto.BulkProfileDTO;
import com.odin.profileservice.dto.MobileListDTO;
import com.odin.profileservice.dto.ProfileDTO;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.enums.CustomerType;
import com.odin.profileservice.factory.CustomerFactory;
import com.odin.profileservice.service.ContactDiscoveryException;
import com.odin.profileservice.utility.PublicKeyRefreshProducer;
import com.odin.profileservice.utility.ResponseObject;

@RestController
@RequestMapping(value = ApplicationConstants.API_VERSION)
public class ProfileController {

	private static final Logger log = LoggerFactory.getLogger(ProfileController.class);

	@Autowired
	ResponseObject response;

	@Autowired
	private CustomerFactory factory;

	@Autowired
	private PublicKeyRefreshProducer publicKeyRefreshProducer;

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
		log.info(
				"[BULK-CUSTOMER-DETAILS][REACHABILITY] stage=controller-entry traceId={} method={} path={} httpStatus=NA requestBytes={}",
				com.odin.profileservice.utility.BulkCustomerDetailsDiagnostics.traceId(servlet),
				servlet.getMethod(),
				servlet.getRequestURI(),
				com.odin.profileservice.utility.BulkCustomerDetailsDiagnostics.requestByteLength(servlet));
		log.info(
				"[BULK-CUSTOMER-DETAILS][REACHABILITY] stage=before-service-call traceId={} method={} path={} httpStatus=NA requestBytes={}",
				com.odin.profileservice.utility.BulkCustomerDetailsDiagnostics.traceId(servlet),
				servlet.getMethod(),
				servlet.getRequestURI(),
				com.odin.profileservice.utility.BulkCustomerDetailsDiagnostics.requestByteLength(servlet));
		try {
			CustomerType customerType = CustomerType.CUSTOMER;
			ResponseDTO result;
			try {
				result = factory.getInstance(customerType)
						.fetchCustomerByMobile(servlet, customerType, mobiles);
			} catch (Exception ex) {
				log.error(
						"[BULK-CUSTOMER-DETAILS][REACHABILITY] stage=service-call-exception traceId={} method={} path={} exceptionClass={} safeErrorCategory={}",
						com.odin.profileservice.utility.BulkCustomerDetailsDiagnostics.traceId(servlet),
						servlet.getMethod(),
						servlet.getRequestURI(),
						ex.getClass().getName(),
						com.odin.profileservice.utility.BulkCustomerDetailsDiagnostics.safeCategory(ex.getClass().getName()),
						ex);
				throw ex;
			}
			log.info(
					"[BULK-CUSTOMER-DETAILS][REACHABILITY] stage=after-service-return traceId={} method={} path={} httpStatus=200 requestBytes={}",
					com.odin.profileservice.utility.BulkCustomerDetailsDiagnostics.traceId(servlet),
					servlet.getMethod(),
					servlet.getRequestURI(),
					com.odin.profileservice.utility.BulkCustomerDetailsDiagnostics.requestByteLength(servlet));
			return new ResponseEntity<>(result, HttpStatus.OK);
		} catch (ContactDiscoveryException ex) {
			ResponseDTO result = ResponseDTO.builder()
					.statusCode(ResponseCodes.FAILURE_CODE)
					.status(ResponseCodes.FAILURE)
					.message(ex.getCode())
					.build();
			org.springframework.http.HttpHeaders headers =
					new org.springframework.http.HttpHeaders();
			if (ex.getRetryAfterSeconds() > 0) {
				headers.set("Retry-After",
						String.valueOf(ex.getRetryAfterSeconds()));
			}
			return new ResponseEntity<>(result, headers, ex.getStatus());
		}
	}
	
	@PostMapping(ApplicationConstants.CUSTOMER + ApplicationConstants.BULK + ApplicationConstants.KEY)
	public ResponseEntity<Object> fetchPublicKey(HttpServletRequest servlet,
			@RequestBody BulkProfileDTO profile) {
		CustomerType customerType = CustomerType.CUSTOMER;
		ResponseDTO response = factory.getInstance(customerType).fetchPublicKey(servlet, customerType, profile);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping(ApplicationConstants.CUSTOMER + ApplicationConstants.KEY + ApplicationConstants.KEY_REFRESH)
	public ResponseEntity<Object> refreshPublicKey(HttpServletRequest servlet,
			@RequestBody ProfileDTO profile) {
		if (profile == null || profile.getCustomerId() == null) {
			ResponseDTO responseDto = response.buildResponse(ApplicationConstants.APP_LANG, ResponseCodes.INVALID_REQUEST);
			return new ResponseEntity<>(responseDto, HttpStatus.OK);
		}

		String customerId = String.valueOf(profile.getCustomerId());
		publicKeyRefreshProducer.publish(customerId);
		ResponseDTO responseDto = response.buildResponse(ApplicationConstants.APP_LANG, ResponseCodes.SUCCESS_CODE);
		return new ResponseEntity<>(responseDto, HttpStatus.OK);
	}
	
	@PostMapping(ApplicationConstants.CUSTOMER + ApplicationConstants.KEY + ApplicationConstants.SAVE)
	public ResponseEntity<Object> savePublicKey(HttpServletRequest servlet,
			@RequestBody AuthDTO profile) {
		CustomerType customerType = CustomerType.CUSTOMER;
		ResponseDTO response = factory.getInstance(customerType).savePublicKey(servlet, customerType, profile);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

}
