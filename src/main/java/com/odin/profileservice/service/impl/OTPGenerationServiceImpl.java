package com.odin.profileservice.service.impl;

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
import com.odin.profileservice.utility.AccountStateValidator;
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
	
	@Value("${otp.expiry.duration.seconds}")
	private int otpExpiryDuration;
	
	@Autowired
	private ResponseObject response;

	@Autowired
	private AccountStateValidator accountStateValidator;
	
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
			if (null == profile) {
				return response.buildResponse(ResponseCodes.USER_NOT_EXISTS);
			}
			if (!accountStateValidator.isEligibleForAuth(profile)) {
				return response.buildResponse(ResponseCodes.USER_NOT_EXISTS);
			}
			if (null == dto.getType()) {
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
					String otp = isStaticOtp || ! dto.getMobile().startsWith("91") ? staticOtp : String.valueOf((int) (Math.random() * 900000) + 100000);

					otpService.saveOtp(dto.getMobile(), otp, dto.getType(), otpExpiryDuration);
					Map<String, String> map = new HashMap<>();
					map.put("otp", otp);
					NotificationDTO notify = NotificationDTO.builder().mobile(dto.getMobile()).notificationId(dto.getType().getValue())
							.channel(NotificationChannel.SMS).map(map).build();
					notification.sendOtpMessage(notify);
				}

				if (dto.getEmail() != null && !dto.getEmail().isEmpty()) {
					String otp = isStaticOtp ? staticOtp : String.valueOf((int) (Math.random() * 900000) + 100000);

					otpService.saveOtp(dto.getEmail(), otp, OTPType.REGISTRATION, otpExpiryDuration);
					Map<String, String> map = new HashMap<>();
					map.put("otp", otp);
					NotificationDTO notify = NotificationDTO.builder().email(dto.getEmail()).notificationId(dto.getType().getValue())
							.channel(NotificationChannel.EMAIL).map(map).build();
					notification.sendOtpMessage(notify);
				}
				return response.buildResponse(ResponseCodes.OTP_SENT_SUCCESSFUL);
			}
		}catch(Exception e) {
			log.error("Failed to generate otp for : {}", dto);
			return response.buildResponse(ResponseCodes.FAILURE_CODE);
		}
	}
	
	

}
