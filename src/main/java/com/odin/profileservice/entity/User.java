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
 * User entity - stores core user profile data with hashed phone numbers.
 * Phone numbers are never stored in raw form.
 */
@Entity
@Table(name = "mw_users", indexes = {
        @Index(name = "idx_phone_hash", columnList = "phone_hash"),
        @Index(name = "idx_global_phone_hash", columnList = "global_phone_hash"),
        @Index(name = "idx_updated_at", columnList = "updated_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @Column(name = "user_id", columnDefinition = "VARCHAR(36)", updatable = false)
    private String userId;

    @Column(name = "phone_hash", nullable = false, unique = true, length = 64)
    private String phoneHash;

    @Column(name = "phone_salt", nullable = false, length = 32)
    private String phoneSalt;

    @Column(name = "global_phone_hash", nullable = false, unique = true, length = 64)
    private String globalPhoneHash;

    @Column(name = "pepper_version", nullable = false)
    private Integer pepperVersion;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "profile_photo_url", length = 2048)
    private String profilePhotoUrl;

    @Column(name = "status_text", length = 500)
    private String statusText;

    @Column(name = "last_seen_timestamp")
    private Long lastSeenTimestamp;

    @Column(name = "photo_version", nullable = false)
    @Builder.Default
    private Integer photoVersion = 0;

    @Column(name = "account_created_at", nullable = false, updatable = false)
    private Timestamp accountCreatedAt;

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    @PrePersist
    protected void onCreate() {
        // Only generate UUID if userId is not set
        // userId can be set explicitly (e.g., from customerId) or auto-generated
        if (userId == null || userId.trim().isEmpty()) {
            userId = UUID.randomUUID().toString();
        }
        if (accountCreatedAt == null) {
            accountCreatedAt = new Timestamp(System.currentTimeMillis());
        }
        if (updatedAt == null) {
            updatedAt = new Timestamp(System.currentTimeMillis());
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Timestamp(System.currentTimeMillis());
    }
}
