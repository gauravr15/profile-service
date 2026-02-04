package com.odin.profileservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.sql.Timestamp;

/**
 * SyncAudit entity - tracks the last time a user performed a contact sync.
 */
@Entity
@Table(name = "mw_sync_audit")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncAudit {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "last_synced_at", nullable = false)
    private Timestamp lastSyncedAt;
}
