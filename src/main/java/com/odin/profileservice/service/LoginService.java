package com.odin.profileservice.service;

import javax.servlet.http.HttpServletRequest;

import com.odin.profileservice.dto.MobileListDTO;
import com.odin.profileservice.dto.ProfileDTO;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.enums.CustomerType;

public interface LoginService {

	ResponseDTO fetch(HttpServletRequest servlet, ProfileDTO profileDTO);

	ResponseDTO fetchProfileDetails(ProfileDTO profileDTO);

	ResponseDTO fetchCustomerId(String type, String mobile);

	ResponseDTO fetchCustomerByMobile(CustomerType customerType, MobileListDTO mobile);

}
