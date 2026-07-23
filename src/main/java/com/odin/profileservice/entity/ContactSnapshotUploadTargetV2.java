package com.odin.profileservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;
import java.sql.Timestamp;

@Entity
@IdClass(ContactSnapshotUploadTargetV2Id.class)
@Table(name = "contact_snapshot_upload_target_v2")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactSnapshotUploadTargetV2 {
    @Id
    @Column(name = "session_id", length = 36, nullable = false)
    private String sessionId;

    @Id
    @Column(name = "target_global_phone_hash", length = 64, nullable = false)
    private String targetGlobalPhoneHash;

    @Column(name = "target_global_phone_token", length = 64)
    private String targetGlobalPhoneToken;

    @Column(name = "target_global_phone_token_version")
    private Integer targetGlobalPhoneTokenVersion;

    @Column(name = "registered", nullable = false)
    private Boolean registered;

    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;
}
