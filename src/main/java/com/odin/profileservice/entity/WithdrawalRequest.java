package com.odin.profileservice.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import com.odin.profileservice.enums.WithdrawalStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "withdrawal_request")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Customer requesting withdrawal
     */
    @Column(name = "customer_id", nullable = false)
    private Integer customerId;

    /**
     * Investment from which withdrawal is requested
     */
    @Column(name = "investment_id", nullable = false)
    private Long investmentId;

    /**
     * Total withdrawal amount
     * principal + profit + cashback
     */
    @Column(name = "requested_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal requestedAmount;

    /**
     * Principal portion
     */
    @Column(name = "principal_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal principalAmount;

    /**
     * Profit portion
     */
    @Column(name = "profit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal profitAmount;

    /**
     * Cashback being withdrawn immediately
     */
    @Column(name = "cashback_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal cashbackAmount;

    /**
     * Cashback that will be credited later
     */
    @Builder.Default
    @Column(name = "cashback_to_be_credited", nullable = false)
    private BigDecimal cashbackToBeCredited = BigDecimal.ZERO;

    /**
     * When customer raised request
     */
    @Column(name = "request_time", nullable = false)
    private LocalDateTime requestTime;

    /**
     * Whether included in payout report
     */
    @Builder.Default
    @Column(name = "report_generated", nullable = false)
    private Boolean reportGenerated = false;

    /**
     * When report was generated
     */
    @Column(name = "report_generated_time")
    private LocalDateTime reportGeneratedTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WithdrawalStatus status;

    /**
     * Admin remarks
     */
    @Column(name = "remarks", length = 1000)
    private String remarks;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}