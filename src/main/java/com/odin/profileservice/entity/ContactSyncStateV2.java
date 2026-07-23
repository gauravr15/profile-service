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
@Table(name = "contact_sync_state_v2")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactSyncStateV2 {
    @Id
    @Column(name = "owner_user_id", length = 36)
    private String ownerUserId;

    @Column(name = "current_revision", nullable = false)
    private Long currentRevision;

    @Column(name = "last_snapshot_id", nullable = false, length = 36)
    private String lastSnapshotId;

    @Column(name = "last_synced_at", nullable = false)
    private Timestamp lastSyncedAt;
}
