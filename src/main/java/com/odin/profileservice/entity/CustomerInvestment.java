package com.odin.profileservice.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "customer_investment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerInvestment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer customerId;

    private BigDecimal principalBalance;

    private BigDecimal profitBalance;

    private BigDecimal cashbackBalance;

    private Long cashbackCycleNo;

    private Long lastCashbackCycleAwarded;

    private LocalDateTime investedAt;

    private LocalDateTime maturityAt;

    private String status;
    
    @ManyToOne
    @JoinColumn(name = "scheme_id")
    private InvestmentScheme scheme;
}