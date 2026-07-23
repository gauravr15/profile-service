package com.odin.profileservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;
import java.sql.Timestamp;

@Entity
@IdClass(ContactSnapshotUploadChunkV2Id.class)
@Table(name = "contact_snapshot_upload_chunk_v2")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactSnapshotUploadChunkV2 {
    @Id
    @Column(name = "session_id", length = 36, nullable = false)
    private String sessionId;

    @Id
    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "payload_digest", length = 64, nullable = false)
    private String payloadDigest;

    @Column(name = "submitted_count", nullable = false)
    private Integer submittedCount;

    @Column(name = "canonical_count", nullable = false)
    private Integer canonicalCount;

    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;
}
