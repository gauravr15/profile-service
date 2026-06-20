package com.odin.profileservice.service;

import com.odin.profileservice.dto.ResponseDTO;

public interface InvestmentService {

	ResponseDTO getInvestmentDetails(Integer customerId, Long investmentId);
}