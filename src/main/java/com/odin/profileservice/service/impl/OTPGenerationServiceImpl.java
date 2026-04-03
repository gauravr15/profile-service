package com.odin.profileservice.service.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.enums.EnumUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.NotificationDTO;
import com.odin.profileservice.dto.OtpRequestDTO;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.entity.Profile;
import com.odin.profileservice.enums.NotificationChannel;
import com.odin.profileservice.enums.OTPType;
import com.odin.profileservice.repo.ProfileRepository;
import com.odin.profileservice.service.OTPGenerationService;
import com.odin.profileservice.utility.NotificationUtility;
import com.odin.profileservice.utility.OtpService;
import com.odin.profileservice.utility.ResponseObject;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OTPGenerationServiceImpl implements OTPGenerationService{
	
	@Value("${is.static.otp}")
	private boolean isStaticOtp;

	@Value("${static.otp}")
	private String staticOtp;

	/** Comma-separated mobile numbers (with country code) that are Google Play test accounts. */
	@Value("${test.customer.list:}")
	private String testCustomerList;

	/** Static OTP returned to test accounts — never sent via SMS. */
	@Value("${test.customer.otp:}")
	private String testCustomerOtp;
	
	@Value("${otp.expiry.duration.seconds}")
	private int otpExpiryDuration;
	
	@Autowired
	private ResponseObject response;
	
	@Autowired
	private ProfileRepository profileRepo;
	
	@Autowired
	private OtpService otpService;
	
	@Autowired
	private NotificationUtility notification;
	
	@Override
	public ResponseDTO generateOtp(HttpServletRequest req, OtpRequestDTO dto) {
		try {
			Profile profile = profileRepo.findByMobileOrEmail(dto.getMobile(), dto.getEmail());
			if(null == profile) {
				return response.buildResponse(ResponseCodes.USER_NOT_EXISTS);
			}else if(null == dto.getType()){
				return response.buildResponse(ResponseCodes.INVALID_REQUEST);
			}else {
				if (dto.getMobile().isEmpty() && dto.getEmail().isEmpty()) {
					return response.buildResponse(ResponseCodes.INVALID_REQUEST);
				}
				String mobileOtp = (dto.getMobile() != null)
						? otpService.getOtp(dto.getMobile(), dto.getType())
						: null;

				if (mobileOtp != null && !mobileOtp.isEmpty()) {
					otpService.clearOtp(dto.getMobile(), dto.getType());
				}

				String emailOtp = (dto.getEmail() != null)
						? otpService.getOtp(dto.getEmail(), dto.getType())
						: null;

				if (emailOtp != null && !emailOtp.isEmpty()) {
					otpService.clearOtp(dto.getEmail(), dto.getType());
				}
				if (dto.getMobile() != null && !dto.getMobile().isEmpty()) {
					boolean isTestCustomer = isTestCustomer(dto.getMobile());
					String otp = isTestCustomer ? testCustomerOtp
							: isStaticOtp ? staticOtp
							: String.valueOf((int) (Math.random() * 900000) + 100000);

					otpService.saveOtp(dto.getMobile(), otp, dto.getType(), otpExpiryDuration);
					if (!isTestCustomer) {
						Map<String, String> map = new HashMap<>();
						map.put("otp", otp);
						NotificationDTO notify = NotificationDTO.builder().mobile(dto.getMobile()).notificationId(dto.getType().getValue())
								.channel(NotificationChannel.SMS).map(map).build();
						notification.sendOtpMessage(notify);
					}
				}

				if (dto.getEmail() != null && !dto.getEmail().isEmpty()) {
					boolean isTestCustomer = isTestCustomer(dto.getEmail());
					String otp = isTestCustomer ? testCustomerOtp
							: isStaticOtp ? staticOtp
							: String.valueOf((int) (Math.random() * 900000) + 100000);

					otpService.saveOtp(dto.getEmail(), otp, OTPType.REGISTRATION, otpExpiryDuration);
					if (!isTestCustomer) {
						Map<String, String> map = new HashMap<>();
						map.put("otp", otp);
						NotificationDTO notify = NotificationDTO.builder().email(dto.getEmail()).notificationId(dto.getType().getValue())
								.channel(NotificationChannel.EMAIL).map(map).build();
						notification.sendOtpMessage(notify);
					}
				}
				return response.buildResponse(ResponseCodes.OTP_SENT_SUCCESSFUL);
			}
		}catch(Exception e) {
			log.error("Failed to generate otp for : {}", dto);
			return response.buildResponse(ResponseCodes.FAILURE_CODE);
		}
	}

	/**
	 * Returns true if the given mobile/email belongs to the Google Play test account list.
	 * Test accounts receive a static OTP and no SMS is dispatched.
	 */
	private boolean isTestCustomer(String identifier) {
		if (testCustomerList == null || testCustomerList.trim().isEmpty()) return false;
		return Arrays.stream(testCustomerList.split(","))
				.map(String::trim)
				.anyMatch(n -> n.equals(identifier));
	}

}
