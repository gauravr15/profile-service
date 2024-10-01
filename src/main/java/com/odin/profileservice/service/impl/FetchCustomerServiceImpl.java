package com.odin.profileservice.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.odin.profileservice.dto.ProfileDTO;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.repo.ProfileRepository;
import com.odin.profileservice.service.FetchService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FetchCustomerServiceImpl implements FetchService{

	@Autowired
	private ProfileRepository profileRepo;
	
	@Override
	public ResponseDTO fetch(ProfileDTO profileDTO) {
		log.info("Loggin in user");
		return profileRepo.validateCustomerProfile(profileDTO);
	}

}
