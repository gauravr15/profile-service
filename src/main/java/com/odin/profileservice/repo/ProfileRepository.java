package com.odin.profileservice.repo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import com.odin.profileservice.dto.ProfileDTO;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.utility.Utility;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ProfileRepository {

	@Value("${core.update.url}")
	private String coreUrl;

	@Autowired
	private Utility utility;

	public ResponseDTO validateCustomerProfile(ProfileDTO profile) {
		log.info("Validating customer profile");
		return utility.makeRestCall(coreUrl + CoreAPIConstants.SIGN_IN ,
				profile, HttpMethod.POST, ResponseDTO.class);
	}

}
