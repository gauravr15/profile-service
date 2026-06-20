package com.odin.profileservice.repo;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.odin.profileservice.entity.InvestmentLedger;

public interface InvestmentLedgerRepository
        extends JpaRepository<InvestmentLedger, Long> {

    Page<InvestmentLedger>
    findByCustomerIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Integer customerId,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable);
}