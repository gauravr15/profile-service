package com.odin.profileservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "account_deletion_outbox")
public class AccountDeletionOutbox {

    public enum Status {
        PENDING,
        PUBLISHED
    }

    @Id
    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "aggregate_type", nullable = false, length = 32)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Timestamp nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;

    @Column(name = "published_at")
    private Timestamp publishedAt;

    @Column(name = "last_error_category", length = 64)
    private String lastErrorCategory;

    @Column(name = "claim_token", length = 36)
    private String claimToken;

    @Column(name = "claim_until")
    private Timestamp claimUntil;
}
