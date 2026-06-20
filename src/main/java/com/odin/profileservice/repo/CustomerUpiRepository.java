package com.odin.profileservice.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.odin.profileservice.entity.CustomerUpi;

@Repository
public interface CustomerUpiRepository extends JpaRepository<CustomerUpi, Long> {

	List<CustomerUpi> findByCustomerIdOrderByIsPrimaryDesc(Integer customerId);

	Optional<CustomerUpi> findByIdAndCustomerId(Long id, Integer customerId);

	List<CustomerUpi> findByCustomerId(Integer customerId);
}