package com.odin.profileservice.service;

import javax.servlet.http.HttpServletRequest;

import com.odin.profileservice.dto.AuthDTO;
import com.odin.profileservice.dto.BulkProfileDTO;
import com.odin.profileservice.dto.MobileListDTO;
import com.odin.profileservice.dto.ProfileDTO;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.enums.CustomerType;

public interface LoginService {

	ResponseDTO fetch(HttpServletRequest servlet, ProfileDTO profileDTO);

	ResponseDTO fetchProfileDetails(ProfileDTO profileDTO);

	ResponseDTO fetchCustomerId(String type, String mobile);

	ResponseDTO fetchCustomerByMobile(HttpServletRequest request, CustomerType customerType, MobileListDTO mobiles);

	ResponseDTO fetchPublicKey(HttpServletRequest servlet, CustomerType customerType, BulkProfileDTO profiles);

	ResponseDTO savePublicKey(HttpServletRequest servlet, CustomerType customerType, AuthDTO auth);

}
