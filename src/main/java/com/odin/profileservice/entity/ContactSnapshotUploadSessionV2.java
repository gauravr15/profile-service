package com.odin.profileservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.sql.Timestamp;

@Entity
@Table(name = "contact_snapshot_upload_session_v2", indexes = {
        @Index(name = "idx_snapshot_upload_v2_owner_state",
                columnList = "owner_user_id,state"),
        @Index(name = "idx_snapshot_upload_v2_state_expiry",
                columnList = "state,expires_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_snapshot_upload_v2_owner_snapshot",
                columnNames = {"owner_user_id", "snapshot_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactSnapshotUploadSessionV2 {
    public enum State {
        OPEN,
        COMMITTED
    }

    @Id
    @Column(name = "session_id", length = 36, nullable = false, updatable = false)
    private String sessionId;

    @Column(name = "owner_user_id", length = 36, nullable = false, updatable = false)
    private String ownerUserId;

    @Column(name = "snapshot_id", length = 36, nullable = false, updatable = false)
    private String snapshotId;

    @Column(name = "base_revision", nullable = false, updatable = false)
    private Long baseRevision;

    @Column(name = "country_code", length = 2, nullable = false, updatable = false)
    private String countryCode;

    @Column(name = "declared_total_contacts", nullable = false, updatable = false)
    private Integer declaredTotalContacts;

    @Column(name = "declared_total_chunks", nullable = false, updatable = false)
    private Integer declaredTotalChunks;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 16, nullable = false)
    private State state;

    @Column(name = "payload_digest", length = 64)
    private String payloadDigest;

    @Column(name = "expires_at", nullable = false)
    private Timestamp expiresAt;

    @Column(name = "committed_at")
    private Timestamp committedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    @Column(name = "contact_lifecycle_epoch", nullable = false, updatable = false)
    @Builder.Default
    private Long contactLifecycleEpoch = 0L;
}
