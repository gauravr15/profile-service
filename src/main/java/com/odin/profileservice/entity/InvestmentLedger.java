package com.odin.profileservice.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import javax.persistence.*;

import lombok.*;

@Entity
@Table(name = "investment_ledger")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestmentLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer customerId;

    private Long investmentId;

    private String txnType;

    private BigDecimal amount;

    private Long referenceId;

    private BigDecimal balanceAfter;

    private String remarks;

    private LocalDateTime createdAt;
}