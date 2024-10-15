package com.odin.profileservice.service;

import com.odin.profileservice.dto.ProfileDTO;
import com.odin.profileservice.dto.ResponseDTO;

public interface FetchService {

	ResponseDTO fetch(ProfileDTO profileDTO);

	ResponseDTO fetchProfileDetails(ProfileDTO profileDTO);

}
