package com.odin.profileservice.service;

import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.dto.WithdrawalPreviewRequest;
import com.odin.profileservice.dto.WithdrawalRequestDto;

public interface WithdrawalService {


	ResponseDTO createWithdrawalRequest(Integer customerId, WithdrawalRequestDto request);

	ResponseDTO getRequests(Integer customerId);

	ResponseDTO cancelWithdrawal(Integer customerId, Long requestId);

	ResponseDTO getLockStatus();

	ResponseDTO getEligibility(Integer customerId, Long investmentId);

	ResponseDTO previewWithdrawal(Integer customerId, WithdrawalPreviewRequest request);
}