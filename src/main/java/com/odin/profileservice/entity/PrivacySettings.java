package com.odin.profileservice.entity;

import com.odin.profileservice.enums.PrivacyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * Privacy settings entity - stores per-user privacy levels for profile attributes.
 * Defaults to MY_CONTACTS for all attributes.
 */
@Entity
@Table(name = "privacy_settings", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivacySettings {

    @Id
    @Column(name = "setting_id", columnDefinition = "VARCHAR(36)", updatable = false)
    private String settingId;

    @Column(name = "user_id", nullable = false, unique = true, length = 36)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "photo_privacy", nullable = false, length = 20)
    private PrivacyLevel photoPrivacy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_privacy", nullable = false, length = 20)
    private PrivacyLevel statusPrivacy;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_seen_privacy", nullable = false, length = 20)
    private PrivacyLevel lastSeenPrivacy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    @PrePersist
    protected void onCreate() {
        if (settingId == null) {
            settingId = UUID.randomUUID().toString();
        }
        if (photoPrivacy == null) {
            photoPrivacy = PrivacyLevel.MY_CONTACTS;
        }
        if (statusPrivacy == null) {
            statusPrivacy = PrivacyLevel.MY_CONTACTS;
        }
        if (lastSeenPrivacy == null) {
            lastSeenPrivacy = PrivacyLevel.MY_CONTACTS;
        }
        if (createdAt == null) {
            createdAt = new Timestamp(System.currentTimeMillis());
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
