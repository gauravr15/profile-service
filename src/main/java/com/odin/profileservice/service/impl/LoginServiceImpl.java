package com.odin.profileservice.service.impl;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.odin.profileservice.constants.ApplicationConstants;
import com.odin.profileservice.constants.LanguageConstants;
import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.CustomerDetailsDTO;
import com.odin.profileservice.dto.JwtDTO;
import com.odin.profileservice.dto.MobileListDTO;
import com.odin.profileservice.dto.ProfileDTO;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.entity.Profile;
import com.odin.profileservice.entity.RefreshToken;
import com.odin.profileservice.enums.CustomerType;
import com.odin.profileservice.enums.OTPType;
import com.odin.profileservice.repo.ProfileRepository;
import com.odin.profileservice.repo.RefreshTokenRepository;
import com.odin.profileservice.service.LoginService;
import com.odin.profileservice.utility.JwtTokenUtil;
import com.odin.profileservice.utility.OtpService;
import com.odin.profileservice.utility.ResponseObject;
import com.odin.profileservice.utility.Utility;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class LoginServiceImpl implements LoginService {

	@Autowired
	private ProfileRepository profileRepo;

	@Autowired
	private ResponseObject response;

	@Autowired
	private JwtTokenUtil jwtTokenUtil;

	@Autowired
	private Utility utility;

	@Autowired
	private OtpService otpService;

	@Autowired
	private RefreshTokenRepository refreshTokenRepo;

	@Value("${max.incorrect.password.count}")
	private String maxIncorrectPasswordCount;

	@Value("${jwt.secret}")
	private String jwtSecret;

	@Value("${jwt.expiration}")
	private long accessTokenExpiryMs;

	@Value("${jwt.refreshExpiration}")
	private long refreshTokenExpiryMs;

	@Value("${jwt.issuer}")
	private String issuer;

	@Override
	public ResponseDTO fetchProfileDetails(ProfileDTO profileDTO) {
		log.info("Fetching customer profile by mobile : {} or email : {}", profileDTO.getMobile(),
				profileDTO.getEmail());
		profileDTO.setMobile(
				ObjectUtils.isEmpty(profileDTO.getMobile()) ? profileDTO.getEmail() : profileDTO.getMobile());
		Profile profile = profileRepo.findByCustomerId(profileDTO.getCustomerId());
		if (ObjectUtils.isEmpty(profile) || (!ObjectUtils.isEmpty(profile)
				&& ObjectUtils.isEmpty(profile.getIsDeleted()) && profile.getIsDeleted())) {
			return response.buildResponse(LanguageConstants.EN, ResponseCodes.USER_NOT_EXISTS);
		}
		return response.buildResponse(LanguageConstants.EN, ResponseCodes.SUCCESS_CODE,
				utility.dtoToEntity(profile, CustomerDetailsDTO.class));
	}

	@Override
	public ResponseDTO fetch(HttpServletRequest servlet, ProfileDTO profileDTO) {
		log.info("Fetching customer profile by mobile : {} or email : {}", profileDTO.getMobile(),
				profileDTO.getEmail());
		String flowAuthType = utility.getAuthType(ApplicationConstants.AUTH_FLOW_SIGNIN);
		log.info("Signup flow auth type from Redis: {}", flowAuthType);

		if (ApplicationConstants.OTP.equalsIgnoreCase(flowAuthType)) {
			return signInViaOtp(servlet, profileDTO);
		}
		String deviceSignature = utility.getDeviceSignature(servlet);

		profileDTO.setMobile(
				ObjectUtils.isEmpty(profileDTO.getMobile()) ? profileDTO.getEmail() : profileDTO.getMobile());

		Profile profile = profileRepo.findByMobileOrEmail(profileDTO.getMobile(), profileDTO.getEmail());
		if (ObjectUtils.isEmpty(profile)) {
			return response.buildResponse(LanguageConstants.EN, ResponseCodes.USER_NOT_EXISTS);
		}

		if (ObjectUtils.isEmpty(profile.getAuth())) {
			log.error("Failed to fetch auth instance for customer with identifier : {}", profileDTO.getMobile());
			return response.buildResponse(LanguageConstants.EN, ResponseCodes.INTERNAL_SERVER_ERROR);
		}

		BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
		boolean isPasswordMatch = passwordEncoder.matches(profileDTO.getAuth().getPassword(),
				profile.getAuth().getPassword());

		if (isPasswordMatch) {
			// Update login details
			profile.getAuth().setLastLoginTimestamp(new Timestamp(System.currentTimeMillis()));
			profile.getAuth().setTempLockCount(0);
			profile.getAuth().setPermLockCount(0);
			profile.getAuth().setIncorrectPasswordCount(0);
			profileRepo.update(profile);

			// Generate tokens
			String accessToken = jwtTokenUtil.generateAccessToken(String.valueOf(profile.getCustomerId()),
					deviceSignature);
			String refreshToken = jwtTokenUtil.generateRefreshToken();

			// Persist refresh token (per device)
			RefreshToken rt = new RefreshToken();
			rt.setCustomerId(Long.valueOf(profile.getCustomerId()));
			rt.setRefreshToken(refreshToken);
			Timestamp now = new Timestamp(System.currentTimeMillis());
			rt.setCreatedAt(now);
			rt.setExpiryDate(new Timestamp(now.getTime() + refreshTokenExpiryMs));
			rt.setIsActive(true);
			rt.setDeviceSignature(deviceSignature);
			RefreshToken existing = refreshTokenRepo.findByCustomerId(Long.valueOf(profile.getCustomerId()));
			if(Objects.isNull(existing)) {
				refreshTokenRepo.save(rt);
			}
			else {
				refreshTokenRepo.delete(existing);
				refreshTokenRepo.save(rt);
			}
			JwtDTO jwtResponse = JwtDTO.builder().accessToken(accessToken).refreshToken(refreshToken)
					.deviceSignature(deviceSignature).build();

			return response.buildResponse(LanguageConstants.EN, ResponseCodes.SUCCESS_CODE, jwtResponse);
		} else {
			// incorrect password handling
			Integer count = ObjectUtils.isEmpty(profile.getAuth().getIncorrectPasswordCount()) ? 1
					: profile.getAuth().getIncorrectPasswordCount() + 1;
			profile.getAuth().setIncorrectPasswordCount(count);

			if (count >= Integer.parseInt(maxIncorrectPasswordCount)) {
				profile.getAuth().setTempLockDate(new Timestamp(System.currentTimeMillis()));
				profile.getAuth().setTempLockCount(1);
			}
			profileRepo.update(profile);
			return response.buildResponse(LanguageConstants.EN, ResponseCodes.FAILURE_CODE);
		}
	}

	private ResponseDTO signInViaOtp(HttpServletRequest servlet, ProfileDTO profileDTO) {
		log.info("Inside customer signin class");
		String deviceSignature = utility.getDeviceSignature(servlet);
		String flowAuthType = utility.getAuthType(ApplicationConstants.AUTH_FLOW_SIGNIN);
		log.info("Signup flow auth type from Redis: {}", flowAuthType);
		Profile checkProfile = profileRepo.findByMobileOrEmail(profileDTO.getMobile(), profileDTO.getEmail());

		if (!ApplicationConstants.OTP.equalsIgnoreCase(flowAuthType) || !checkProfile.getAuth().isOtpLogin()) {
			return response.buildResponse(ResponseCodes.INVALID_REQUEST);
		}
		String sentOtp = otpService.getOtp(profileDTO.getMobile(), OTPType.SIGNIN);

		if (!sentOtp.equals(profileDTO.getAuth().getPassword())) {
			return response.buildResponse(ResponseCodes.OTP_INVALID);
		} else {
			checkProfile.getAuth().setLastLoginTimestamp(new Timestamp(System.currentTimeMillis()));
			checkProfile.getAuth().setTempLockCount(0);
			checkProfile.getAuth().setPermLockCount(0);
			checkProfile.getAuth().setIncorrectPasswordCount(0);
			profileRepo.update(checkProfile);

			// Generate tokens
			String accessToken = jwtTokenUtil.generateAccessToken(String.valueOf(checkProfile.getCustomerId()),
					deviceSignature);
			String refreshToken = jwtTokenUtil.generateRefreshToken();

			// Persist refresh token (per device)
			RefreshToken rt = new RefreshToken();
			rt.setCustomerId(Long.valueOf(checkProfile.getCustomerId()));
			rt.setRefreshToken(refreshToken);
			Timestamp now = new Timestamp(System.currentTimeMillis());
			rt.setCreatedAt(now);
			rt.setExpiryDate(new Timestamp(now.getTime() + refreshTokenExpiryMs));
			rt.setIsActive(true);
			rt.setDeviceSignature(deviceSignature);
			RefreshToken existing = refreshTokenRepo.findByCustomerId(Long.valueOf(checkProfile.getCustomerId()));
			if(Objects.isNull(existing)) {
				refreshTokenRepo.save(rt);
			}
			else {
				refreshTokenRepo.delete(existing);
				refreshTokenRepo.save(rt);
			}

			JwtDTO jwtResponse = JwtDTO.builder().accessToken(accessToken).refreshToken(refreshToken)
					.deviceSignature(deviceSignature).build();
			otpService.clearOtp(profileDTO.getMobile(), OTPType.SIGNIN);
			otpService.clearOtp(profileDTO.getEmail(), OTPType.SIGNIN);
			return response.buildResponse(LanguageConstants.EN, ResponseCodes.SUCCESS_CODE, jwtResponse);
		}

	}

	@Override
	public ResponseDTO fetchCustomerId(String type, String mobile) {
		log.info("Fetching customer profile by type : {} or identifier : {}", type, mobile);
		Profile profile = profileRepo.findByMobileOrEmailAndCustomerType(mobile, mobile, type);
		if (ObjectUtils.isEmpty(profile) || (!ObjectUtils.isEmpty(profile)
				&& ObjectUtils.isEmpty(profile.getIsDeleted()) && profile.getIsDeleted())) {
			return response.buildResponse(LanguageConstants.EN, ResponseCodes.USER_NOT_EXISTS);
		}
		return response.buildResponse(LanguageConstants.EN, ResponseCodes.SUCCESS_CODE, profile.getCustomerId());
	}

	@Override
	public ResponseDTO fetchCustomerByMobile(CustomerType customerType, MobileListDTO mobiles) {
	    log.info("Fetching fetchCustomerByMobile");

	    PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
	    Set<String> normalizedMobiles = new HashSet<>();
	    Map<String, String> mobileMap = new HashMap<>();

	    // Extract region from provided country code
	    String countryCode = mobiles.getCountryCode() != null
	            ? mobiles.getCountryCode().replaceAll("[^0-9]", "")
	            : null;

	    String region = null;
	    if (countryCode != null) {
	        try {
	            int code = Integer.parseInt(countryCode);
	            List<String> regions = phoneNumberUtil.getRegionCodesForCountryCode(code);
	            if (!regions.isEmpty()) {
	                region = regions.get(0);
	            }
	        } catch (Exception ex) {
	            log.warn("Invalid country code: {}", countryCode);
	        }
	    }

	    // Normalize incoming numbers (FULLY FIXED)
	    for (String rawMobile : mobiles.getMobile()) {
	        if (rawMobile == null) continue;

	        String cleaned = rawMobile.replaceAll("[^0-9+]", "");

	        String normalized = null;

	        try {
	            PhoneNumber phoneNumber = null;

	            // CASE 1: starts with + → parse directly
	            if (cleaned.startsWith("+")) {
	                phoneNumber = phoneNumberUtil.parse(cleaned, null);
	            } else {
	                // CASE 2: Try using logged-in user's region
	                try {
	                    phoneNumber = phoneNumberUtil.parse(cleaned, region);

	                    // If invalid, fallback to international
	                    if (!phoneNumberUtil.isValidNumber(phoneNumber)) {
	                        phoneNumber = phoneNumberUtil.parse("+" + cleaned, null);
	                    }
	                } catch (Exception e) {
	                    // Fallback for numbers like 66629..., 9199..., etc.
	                    phoneNumber = phoneNumberUtil.parse("+" + cleaned, null);
	                }
	            }

	            // Final validation
	            if (phoneNumberUtil.isValidNumber(phoneNumber)) {
	                normalized = phoneNumberUtil
	                        .format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164);

	                if (normalized.startsWith("+")) {
	                    normalized = normalized.substring(1);
	                }
	            }

	        } catch (Exception e) {
	            log.warn("Parse failed for {}: {}", rawMobile, e.getMessage());
	        }

	        // Only add if valid
	        if (normalized != null) {
	            normalizedMobiles.add(normalized);
	            mobileMap.put(normalized, rawMobile);
	        } else {
	            log.warn("Invalid mobile skipped: {}", rawMobile);
	        }
	    }

	    if (normalizedMobiles.isEmpty()) {
	        return response.buildResponse(LanguageConstants.EN, ResponseCodes.FAILURE_CODE);
	    }

	    // DB lookup
	    List<Profile> profiles =
	            profileRepo.findLikeMobileNumber(new ArrayList<>(normalizedMobiles), true);

	    Map<String, CustomerDetailsDTO> result = new HashMap<>();

	    for (Profile profile : profiles) {
	        String normalizedProfileMobile = profile.getMobile().replaceAll("[^0-9]", "");

	        String reqMobile =
	                mobileMap.getOrDefault(normalizedProfileMobile, normalizedProfileMobile);

	        CustomerDetailsDTO dto = CustomerDetailsDTO.builder()
	                .customerId(profile.getCustomerId())
	                .firstName(profile.getFirstName())
	                .lastName(profile.getLastName())
	                .mobile(profile.getMobile())
	                .build();

	        result.put(reqMobile, dto);
	    }

	    if (result.isEmpty()) {
	        return response.buildResponse(LanguageConstants.EN, ResponseCodes.FAILURE_CODE);
	    }

	    return response.buildResponse(LanguageConstants.EN, ResponseCodes.SUCCESS_CODE, result);
	}


}
