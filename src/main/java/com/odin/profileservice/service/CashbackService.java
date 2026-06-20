package com.odin.profileservice.service;

import com.odin.profileservice.dto.ResponseDTO;

public interface CashbackService {

    ResponseDTO getHistory(Integer customerId);

    ResponseDTO getSchedules(Integer customerId);
}