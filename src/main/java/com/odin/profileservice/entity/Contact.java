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
 * Contact entity - represents one-way contact relationship (A saves B's number).
 * owner_user_id saves target_global_phone_hash for deterministic cross-user matching.
 */
@Entity
@Table(name = "contacts", indexes = {
        @Index(name = "idx_owner_target", columnList = "owner_user_id,target_global_phone_hash"),
        @Index(name = "idx_owner_user_id", columnList = "owner_user_id"),
        @Index(name = "idx_target_global_phone_hash", columnList = "target_global_phone_hash"),
        @Index(name = "idx_saved_at", columnList = "saved_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_owner_target", columnNames = {"owner_user_id", "target_global_phone_hash"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Contact {

    @Id
    @Column(name = "contact_id", columnDefinition = "VARCHAR(36)", updatable = false)
    private String contactId;

    @Column(name = "owner_user_id", nullable = false, length = 36)
    private String ownerUserId;

    @Column(name = "target_global_phone_hash", nullable = false, length = 64)
    private String targetGlobalPhoneHash;

    @Column(name = "target_global_phone_token", length = 64)
    private String targetGlobalPhoneToken;

    @Column(name = "target_global_phone_token_version")
    private Integer targetGlobalPhoneTokenVersion;

    // NOTE: contact_name is NOT persisted on server (client-side only)
    // This ensures privacy: server never stores contact labels assigned by user

    @Column(name = "saved_at", nullable = false, updatable = false)
    private Timestamp savedAt;

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    @PrePersist
    protected void onCreate() {
        if (contactId == null) {
            contactId = UUID.randomUUID().toString();
        }
        savedAt = new Timestamp(System.currentTimeMillis());
        updatedAt = new Timestamp(System.currentTimeMillis());
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Timestamp(System.currentTimeMillis());
    }
}
