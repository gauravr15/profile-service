package com.odin.profileservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.sql.Timestamp;
import java.util.UUID;

@Entity
@Table(name = "contacts_v2", indexes = {
        @Index(name = "idx_contacts_v2_owner", columnList = "owner_user_id"),
        @Index(name = "idx_contacts_v2_target", columnList = "target_global_phone_hash")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_contacts_v2_owner_target",
                columnNames = {"owner_user_id", "target_global_phone_hash"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactV2 {
    @Id
    @Column(name = "contact_id", length = 36, updatable = false)
    private String contactId;

    @Column(name = "owner_user_id", nullable = false, length = 36)
    private String ownerUserId;

    @Column(name = "target_global_phone_hash", nullable = false, length = 64)
    private String targetGlobalPhoneHash;

    @Column(name = "target_global_phone_token", length = 64)
    private String targetGlobalPhoneToken;

    @Column(name = "target_global_phone_token_version")
    private Integer targetGlobalPhoneTokenVersion;

    @Column(name = "saved_at", nullable = false, updatable = false)
    private Timestamp savedAt;

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    @PrePersist
    void onCreate() {
        if (contactId == null) {
            contactId = UUID.randomUUID().toString();
        }
        Timestamp now = new Timestamp(System.currentTimeMillis());
        if (savedAt == null) {
            savedAt = now;
        }
        updatedAt = now;
    }
}
