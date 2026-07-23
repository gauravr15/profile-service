package com.odin.profileservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.sql.Timestamp;

@Entity
@Table(name = "contact_snapshot_owner_lock_v2")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactSnapshotOwnerLockV2 {
    @Id
    @Column(name = "owner_user_id", length = 36, nullable = false)
    private String ownerUserId;

    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;

    @Column(name = "contact_lifecycle_epoch", nullable = false)
    @Builder.Default
    private Long contactLifecycleEpoch = 0L;
}
