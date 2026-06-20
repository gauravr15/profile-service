package com.odin.profileservice.service.impl;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.InvestmentDetailsResponse;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.entity.CustomerInvestment;
import com.odin.profileservice.repo.CustomerInvestmentRepository;
import com.odin.profileservice.service.InvestmentService;
import com.odin.profileservice.utility.ResponseObject;

@Service
public class InvestmentServiceImpl implements InvestmentService {

    @Autowired
    private CustomerInvestmentRepository investmentRepo;

    @Autowired
    private ResponseObject response;

    @Override
    public ResponseDTO getInvestmentDetails(
            Integer customerId,
            Long investmentId) {

        Optional<CustomerInvestment> optionalInvestment =
                investmentRepo.findByIdAndCustomerId(
                        investmentId,
                        customerId);

        if (!optionalInvestment.isPresent()) {

            return response.buildResponse(
                    ResponseCodes.NO_DATA_FOUND);
        }

        CustomerInvestment investment =
                optionalInvestment.get();

        InvestmentDetailsResponse dto =
                InvestmentDetailsResponse.builder()
                        .investmentId(
                                investment.getId())
                        .schemeId(
                                investment.getScheme())
                        .schemeName(
                                investment.getScheme() != null
                                        ? investment.getScheme()
                                                .getSchemeName()
                                        : null)
                        .principalBalance(
                                investment.getPrincipalBalance())
                        .profitBalance(
                                investment.getProfitBalance())
                        .eligibleCashback(
                                investment.getCashbackBalance())
                        .lockedCashback(
                                BigDecimal.ZERO)
                        .investedAt(
                                investment.getInvestedAt())
                        .maturityAt(
                                investment.getMaturityAt())
                        .status(
                                investment.getStatus())
                        .build();

        return response.buildResponse(
                ResponseCodes.SUCCESS_CODE,
                dto);
    }
}