package com.odin.profileservice.service;

import com.odin.profileservice.dto.RefreshRequestDTO;
import com.odin.profileservice.dto.ResponseDTO;

public interface TokenService {

	ResponseDTO refresh(RefreshRequestDTO request);

	ResponseDTO revoke(RefreshRequestDTO request);

}
