package com.odin.profileservice.entity;

import com.odin.profileservice.enums.ContactExceptionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * Contact exception entity - stores privacy rule exceptions for specific contacts.
 * Allows overriding default privacy settings for individual contacts.
 */
@Entity
@Table(name = "contact_exceptions", indexes = {
        @Index(name = "idx_exception_owner_user_id", columnList = "owner_user_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_owner_exception", columnNames = {"owner_user_id", "exception_global_phone_hash"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactException {

    @Id
    @Column(name = "exception_id", columnDefinition = "VARCHAR(36)", updatable = false)
    private String exceptionId;

    @Column(name = "owner_user_id", nullable = false, length = 36)
    private String ownerUserId;

    @Column(name = "exception_global_phone_hash", nullable = false, length = 64)
    private String exceptionGlobalPhoneHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "exception_type", nullable = false, length = 20)
    private ContactExceptionType exceptionType;

    @Column(name = "override_privacy_level")
    private Integer overridePrivacyLevel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    @PrePersist
    protected void onCreate() {
        if (exceptionId == null) {
            exceptionId = UUID.randomUUID().toString();
        }
        createdAt = new Timestamp(System.currentTimeMillis());
    }
}
