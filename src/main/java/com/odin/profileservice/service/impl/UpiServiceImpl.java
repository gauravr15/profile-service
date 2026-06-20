package com.odin.profileservice.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.AddUpiRequest;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.dto.UpiResponse;
import com.odin.profileservice.entity.CustomerUpi;
import com.odin.profileservice.repo.CustomerUpiRepository;
import com.odin.profileservice.service.UpiService;
import com.odin.profileservice.utility.ResponseObject;

@Service
public class UpiServiceImpl implements UpiService {

    @Autowired
    private CustomerUpiRepository upiRepo;

    @Autowired
    private ResponseObject response;

    @Override
    public ResponseDTO getUpis(Integer customerId) {

        List<UpiResponse> result =
                upiRepo.findByCustomerIdOrderByIsPrimaryDesc(
                                customerId)
                        .stream()
                        .map(upi ->
                                UpiResponse.builder()
                                        .id(upi.getId())
                                        .upiId(upi.getUpiId())
                                        .holderName(
                                                upi.getAccountHolderName())
                                        .primary(
                                                upi.getIsPrimary())
                                        .verified(
                                                upi.getIsVerified())
                                        .build())
                        .collect(Collectors.toList());

        return response.buildResponse(
                ResponseCodes.SUCCESS_CODE,
                result);
    }

    @Override
    @Transactional
    public ResponseDTO addUpi(
            Integer customerId,
            AddUpiRequest request) {

        boolean firstUpi =
                upiRepo.findByCustomerId(customerId)
                        .isEmpty();

        CustomerUpi entity =
                CustomerUpi.builder()
                        .customerId(customerId)
                        .upiId(request.getUpiId())
                        .accountHolderName(
                                request.getAccountHolderName())
                        .isPrimary(firstUpi)
                        .isVerified(false)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

        upiRepo.save(entity);

        return response.buildResponse(
                ResponseCodes.SUCCESS_CODE,
                "UPI Added");
    }

    @Override
    @Transactional
    public ResponseDTO makePrimary(
            Integer customerId,
            Long upiId) {

        Optional<CustomerUpi> optional =
                upiRepo.findByIdAndCustomerId(
                        upiId,
                        customerId);

        if (!optional.isPresent()) {
            return response.buildResponse(
                    ResponseCodes.NO_DATA_FOUND);
        }

        List<CustomerUpi> allUpis =
                upiRepo.findByCustomerId(customerId);

        for (CustomerUpi upi : allUpis) {

            upi.setIsPrimary(
                    upi.getId().equals(upiId));

            upiRepo.save(upi);
        }

        return response.buildResponse(
                ResponseCodes.SUCCESS_CODE,
                "Primary UPI updated");
    }

    @Override
    @Transactional
    public ResponseDTO deleteUpi(
            Integer customerId,
            Long upiId) {

        Optional<CustomerUpi> optional =
                upiRepo.findByIdAndCustomerId(
                        upiId,
                        customerId);

        if (!optional.isPresent()) {
            return response.buildResponse(
                    ResponseCodes.NO_DATA_FOUND);
        }

        CustomerUpi upi = optional.get();

        boolean wasPrimary =
                Boolean.TRUE.equals(
                        upi.getIsPrimary());

        upiRepo.delete(upi);

        if (wasPrimary) {

            List<CustomerUpi> remaining =
                    upiRepo.findByCustomerId(customerId);

            if (!remaining.isEmpty()) {

                CustomerUpi next =
                        remaining.get(0);

                next.setIsPrimary(true);

                upiRepo.save(next);
            }
        }

        return response.buildResponse(
                ResponseCodes.SUCCESS_CODE,
                "UPI removed");
    }
}