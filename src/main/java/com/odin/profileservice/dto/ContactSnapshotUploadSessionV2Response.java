package com.odin.profileservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class ContactSnapshotUploadSessionV2Response {
    @JsonProperty("session_id")
    String sessionId;

    @JsonProperty("snapshot_id")
    String snapshotId;

    @JsonProperty("base_revision")
    long baseRevision;

    @JsonProperty("chunk_size")
    int chunkSize;

    @JsonProperty("total_chunks")
    int totalChunks;

    @JsonProperty("expires_at")
    Instant expiresAt;
}
