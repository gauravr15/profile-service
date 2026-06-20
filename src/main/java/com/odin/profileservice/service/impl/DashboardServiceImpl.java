package com.odin.profileservice.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.DashboardSummaryResponse;
import com.odin.profileservice.dto.InvestmentSummaryResponse;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.entity.CustomerInvestment;
import com.odin.profileservice.enums.WithdrawalStatus;
import com.odin.profileservice.repo.CustomerInvestmentRepository;
import com.odin.profileservice.repo.WithdrawalRequestRepository;
import com.odin.profileservice.service.DashboardService;
import com.odin.profileservice.utility.ResponseObject;

@Service
public class DashboardServiceImpl implements DashboardService {

    @Autowired
    private CustomerInvestmentRepository investmentRepo;

    @Autowired
    private WithdrawalRequestRepository withdrawalRepo;

    @Autowired
    private ResponseObject response;

    @Override
    public ResponseDTO getDashboardSummary(Integer customerId) {

        List<CustomerInvestment> investments =
                investmentRepo.findByCustomerIdAndStatus(
                        customerId,
                        "ACTIVE");

        BigDecimal totalPrincipal = BigDecimal.ZERO;
        BigDecimal totalProfit = BigDecimal.ZERO;
        BigDecimal eligibleCashback = BigDecimal.ZERO;
        BigDecimal lockedCashback = BigDecimal.ZERO;

        for (CustomerInvestment investment : investments) {

            totalPrincipal =
                    totalPrincipal.add(
                            investment.getPrincipalBalance());

            totalProfit =
                    totalProfit.add(
                            investment.getProfitBalance());

            eligibleCashback =
                    eligibleCashback.add(
                            investment.getCashbackBalance());

            BigDecimal totalEarnedCashback =
                    investment.getCashbackBalance();

            BigDecimal availableCashback =
                    investment.getCashbackBalance();

            lockedCashback =
                    lockedCashback.add(
                            totalEarnedCashback.subtract(
                                    availableCashback));
        }

        Long pendingWithdrawals =
                withdrawalRepo.countByCustomerIdAndStatusIn(
                        customerId,
                        Arrays.asList(
                        		WithdrawalStatus.PENDING,
                        		WithdrawalStatus.APPROVED_UNPAID));

        DashboardSummaryResponse dto =
                DashboardSummaryResponse.builder()
                        .totalPrincipal(totalPrincipal)
                        .totalProfit(totalProfit)
                        .eligibleCashback(eligibleCashback)
                        .lockedCashback(lockedCashback)
                        .activeInvestments(Long.valueOf(investments.size()))
                        .pendingWithdrawals(pendingWithdrawals)
                        .build();

        return response.buildResponse(ResponseCodes.SUCCESS_CODE, dto);
    }

    @Override
    public ResponseDTO getInvestments(Integer customerId) {

        List<CustomerInvestment> investments =
                investmentRepo.findByCustomerIdAndStatus(
                        customerId,
                        "ACTIVE");

        List<InvestmentSummaryResponse> result =
                new ArrayList<>();

        for (CustomerInvestment investment : investments) {

            InvestmentSummaryResponse dto =
                    InvestmentSummaryResponse.builder()
                            .investmentId(investment.getId())
                            .schemeId(
                                    investment.getScheme().getId())
                            .schemeName(
                                    investment.getScheme()
                                            .getSchemeName())
                            .principalBalance(
                                    investment.getPrincipalBalance())
                            .profitBalance(
                                    investment.getProfitBalance())
                            .eligibleCashback(
                                    investment.getCashbackBalance())
                            .lockedCashback(
                                    BigDecimal.ZERO)
                            .maturityDate(
                                    investment.getMaturityAt()
                                            .toLocalDate())
                            .status(
                                    investment.getStatus())
                            .build();

            result.add(dto);
        }

        return response.buildResponse(
                ResponseCodes.SUCCESS_CODE, result);
    }
}