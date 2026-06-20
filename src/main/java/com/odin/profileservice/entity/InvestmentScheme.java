package com.odin.profileservice.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "investment_scheme")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestmentScheme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String schemeName;

    private Integer durationDays;

    private BigDecimal returnPercentage;

    private String status;

    private LocalDateTime createdAt;
}