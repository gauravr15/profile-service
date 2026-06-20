package com.odin.profileservice.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.odin.profileservice.entity.CustomerInvestment;

@Repository
public interface CustomerInvestmentRepository
        extends JpaRepository<CustomerInvestment, Long> {

    List<CustomerInvestment> findByCustomerIdAndStatus(
            Integer customerId,
            String status);

    Optional<CustomerInvestment> findByIdAndCustomerId(
            Long id,
            Integer customerId);
    
    Long countByCustomerIdAndStatusIn(
            Integer customerId,
            List<String> statuses);
}