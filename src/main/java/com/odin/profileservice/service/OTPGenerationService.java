package com.odin.profileservice.service;

import javax.servlet.http.HttpServletRequest;

import com.odin.profileservice.dto.OtpRequestDTO;
import com.odin.profileservice.dto.ResponseDTO;

public interface OTPGenerationService {

	ResponseDTO generateOtp(HttpServletRequest req, OtpRequestDTO dto);

}
