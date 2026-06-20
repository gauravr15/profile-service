package com.odin.profileservice.entity;

import java.math.BigDecimal;

import javax.persistence.*;

import lombok.*;

@Entity
@Table(name = "cashback_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashbackRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal withdrawalPercentFrom;

    private BigDecimal withdrawalPercentTo;

    private BigDecimal cashbackPercent;

    private Boolean active;
}