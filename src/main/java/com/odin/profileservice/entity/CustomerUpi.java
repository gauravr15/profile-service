package com.odin.profileservice.entity;

import java.time.LocalDateTime;

import javax.persistence.*;

import lombok.*;

@Entity
@Table(name = "customer_upi")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerUpi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer customerId;

    private String upiId;

    private String accountHolderName;

    private Boolean isPrimary;

    private Boolean isVerified;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}