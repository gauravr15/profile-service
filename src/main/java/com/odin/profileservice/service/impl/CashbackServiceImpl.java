package com.odin.profileservice.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.CashbackHistoryResponse;
import com.odin.profileservice.dto.CashbackScheduleResponse;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.repo.CashbackScheduleRepository;
import com.odin.profileservice.service.CashbackService;
import com.odin.profileservice.utility.ResponseObject;

@Service
public class CashbackServiceImpl implements CashbackService {

	@Autowired
	private ResponseObject response;
	
	@Autowired
	private CashbackScheduleRepository cashbackScheduleRepo;
	
	@Override
	public ResponseDTO getHistory(
	        Integer customerId) {

	    List<CashbackHistoryResponse> result =
	            cashbackScheduleRepo
	                    .findByCustomerAndStatus(
	                            customerId,
	                            "CREDITED")
	                    .stream()
	                    .map(x ->
	                            CashbackHistoryResponse.builder()
	                                    .cashbackAmount(
	                                            x.getCashbackAmount())
	                                    .creditedAt(
	                                            x.getCreditedAt())
	                                    .investmentId(
	                                            x.getInvestmentId())
	                                    .build())
	                    .collect(Collectors.toList());

	    return response.buildResponse(
	            ResponseCodes.SUCCESS_CODE,
	            result);
	}

	@Override
	public ResponseDTO getSchedules(
	        Integer customerId) {

	    List<CashbackScheduleResponse> result =
	            cashbackScheduleRepo
	                    .findByCustomerAndStatus(
	                            customerId,
	                            "PENDING")
	                    .stream()
	                    .map(x ->
	                            CashbackScheduleResponse.builder()
	                                    .amount(
	                                            x.getCashbackAmount())
	                                    .eligibleAt(
	                                            x.getEligibleAt())
	                                    .status(
	                                            x.getStatus())
	                                    .build())
	                    .collect(Collectors.toList());

	    return response.buildResponse(
	            ResponseCodes.SUCCESS_CODE,
	            result);
	}
}