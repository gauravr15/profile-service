package com.odin.profileservice.repo;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.odin.profileservice.entity.CashbackRule;

@Repository
public interface CashbackRuleRepository
        extends JpaRepository<CashbackRule, Long> {

    CashbackRule
    findFirstByWithdrawalPercentFromLessThanEqualAndWithdrawalPercentToGreaterThanEqualAndActive(
            BigDecimal from,
            BigDecimal to,
            Boolean active);
}