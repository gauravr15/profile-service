package com.odin.profileservice.service;

import com.odin.profileservice.dto.AddUpiRequest;
import com.odin.profileservice.dto.ResponseDTO;

public interface UpiService {

	ResponseDTO getUpis(Integer customerId);

	ResponseDTO addUpi(Integer customerId, AddUpiRequest request);

	ResponseDTO makePrimary(Integer customerId, Long upiId);

	ResponseDTO deleteUpi(Integer customerId, Long upiId);

}