package com.odin.profileservice.service;

import com.odin.profileservice.dto.ResponseDTO;

public interface AccountDeletionService {

    ResponseDTO deleteAccount(String customerId);
}
