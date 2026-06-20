package com.odin.profileservice.service;

import com.odin.profileservice.dto.ResponseDTO;

public interface DashboardService {

    ResponseDTO getDashboardSummary(Integer customerId);

    ResponseDTO getInvestments(Integer customerId);
}