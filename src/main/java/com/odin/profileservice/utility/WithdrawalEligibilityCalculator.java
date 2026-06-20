package com.odin.profileservice.utility;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.odin.profileservice.entity.CustomerInvestment;

@Component
public class WithdrawalEligibilityCalculator {

    public BigDecimal calculateEligibleCashback(
            CustomerInvestment investment) {

        return investment.getCashbackBalance();
    }

    public BigDecimal calculateLockedCashback(
            CustomerInvestment investment) {

        return BigDecimal.ZERO;
    }
}