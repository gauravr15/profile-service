package com.odin.profileservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * Blocked contact entity - represents blocked user relationships.
 * blocker_user_id has blocked blocked_global_phone_hash (one-way block).
 */
@Entity
@Table(name = "blocked_contacts", indexes = {
        @Index(name = "idx_blocker_user_id", columnList = "blocker_user_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_blocker_blocked", columnNames = {"blocker_user_id", "blocked_global_phone_hash"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockedContact {

    @Id
    @Column(name = "block_id", columnDefinition = "VARCHAR(36)", updatable = false)
    private String blockId;

    @Column(name = "blocker_user_id", nullable = false, length = 36)
    private String blockerUserId;

    @Column(name = "blocked_global_phone_hash", nullable = false, length = 64)
    private String blockedGlobalPhoneHash;

    @Column(name = "blocked_global_phone_token", length = 64)
    private String blockedGlobalPhoneToken;

    @Column(name = "blocked_global_phone_token_version")
    private Integer blockedGlobalPhoneTokenVersion;

    @Column(name = "blocked_at", nullable = false, updatable = false)
    private Timestamp blockedAt;

    @PrePersist
    protected void onCreate() {
        if (blockId == null) {
            blockId = UUID.randomUUID().toString();
        }
        blockedAt = new Timestamp(System.currentTimeMillis());
    }
}
