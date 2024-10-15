package com.odin.profileservice.service.impl;

import java.sql.Timestamp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import com.odin.profileservice.constants.LanguageConstants;
import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.CustomerDetailsDTO;
import com.odin.profileservice.dto.JwtDTO;
import com.odin.profileservice.dto.ProfileDTO;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.entity.Profile;
import com.odin.profileservice.repo.ProfileRepository;
import com.odin.profileservice.service.FetchService;
import com.odin.profileservice.utility.JwtTokenUtil;
import com.odin.profileservice.utility.ResponseObject;
import com.odin.profileservice.utility.Utility;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FetchCustomerServiceImpl implements FetchService {

	@Autowired
	private ProfileRepository profileRepo;

	@Autowired
	private ResponseObject response;

	@Autowired
	private JwtTokenUtil jwtTokenUtil;

	@Autowired
	private Utility utility;

	@Value("${max.incorrect.password.count}")
	private String maxIncorrectPasswordCount;

	@Override
	public ResponseDTO fetch(ProfileDTO profileDTO) {
		log.info("Fetching customer profile by mobile : {} or email : {}", profileDTO.getMobile(),
				profileDTO.getEmail());
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

			// Generate JWT token
			String token = jwtTokenUtil.generateToken(String.valueOf(profile.getCustomerId()));
			JwtDTO jwtResponse = JwtDTO.builder().accessToken(token).build();
			// Return response with JWT token
			return response.buildResponse(LanguageConstants.EN, ResponseCodes.SUCCESS_CODE, jwtResponse);
		} else {
			// Handle incorrect password case
			profile.getAuth()
					.setIncorrectPasswordCount(ObjectUtils.isEmpty(profile.getAuth().getIncorrectPasswordCount()) ? 1
							: profile.getAuth().getIncorrectPasswordCount());
			if (profile.getAuth().getIncorrectPasswordCount() == Integer.valueOf(maxIncorrectPasswordCount)) {
				profile.getAuth().setTempLockDate(new Timestamp(System.currentTimeMillis()));
				profile.getAuth().setTempLockCount(1);
			}
			profileRepo.update(profile);
			return response.buildResponse(LanguageConstants.EN, ResponseCodes.FAILURE_CODE);
		}
	}

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

}
