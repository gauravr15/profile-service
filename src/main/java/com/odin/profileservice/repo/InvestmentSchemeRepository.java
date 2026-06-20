package com.odin.profileservice.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.odin.profileservice.entity.InvestmentScheme;

@Repository
public interface InvestmentSchemeRepository extends JpaRepository<InvestmentScheme, Long> {

}