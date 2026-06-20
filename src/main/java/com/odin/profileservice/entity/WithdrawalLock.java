package com.odin.profileservice.entity;

import java.time.LocalDateTime;

import javax.persistence.*;

import lombok.*;

@Entity
@Table(name = "withdrawal_lock")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithdrawalLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime lockedAt;

    private LocalDateTime unlockAt;

    private String reason;

    private Boolean isManual;

    private String status;
}