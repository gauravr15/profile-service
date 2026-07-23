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
import javax.persistence.IdClass;
import javax.persistence.Index;
import javax.persistence.Table;
import java.sql.Timestamp;

@Entity
@IdClass(ContactSnapshotRequestV2Id.class)
@Table(name = "contact_snapshot_request_v2", indexes = {
        @Index(name = "idx_snapshot_request_v2_owner_created",
                columnList = "owner_user_id,created_at"),
        @Index(name = "idx_snapshot_request_v2_owner_revision",
                columnList = "owner_user_id,committed_revision")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactSnapshotRequestV2 {
    public enum Status {
        COMMITTED,
        TOMBSTONE
    }

    @Id
    @Column(name = "owner_user_id", length = 36, nullable = false)
    private String ownerUserId;

    @Id
    @Column(name = "snapshot_id", length = 36, nullable = false)
    private String snapshotId;

    @Column(name = "payload_digest", length = 64, nullable = false)
    private String payloadDigest;

    @Column(name = "base_revision", nullable = false)
    private Long baseRevision;

    @Column(name = "committed_revision", nullable = false)
    private Long committedRevision;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private Status status;

    @Column(name = "submitted_count")
    private Integer submittedCount;

    @Column(name = "canonical_count")
    private Integer canonicalCount;

    @Column(name = "added_count")
    private Integer addedCount;

    @Column(name = "removed_count")
    private Integer removedCount;

    @Column(name = "retained_count")
    private Integer retainedCount;

    @Column(name = "registered_count")
    private Integer registeredCount;

    @Column(name = "unregistered_count")
    private Integer unregisteredCount;

    @Column(name = "rejected_count")
    private Integer rejectedCount;

    @Column(name = "synced_at")
    private Timestamp syncedAt;

    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    @Column(name = "contact_lifecycle_epoch", nullable = false)
    @Builder.Default
    private Long contactLifecycleEpoch = 0L;
}
