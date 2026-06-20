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
@Table(name = "cashback_schedule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashbackSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long withdrawalRequestId;

    private Long investmentId;

    private BigDecimal cashbackAmount;

    private LocalDateTime eligibleAt;

    private LocalDateTime creditedAt;

    private String status;
}