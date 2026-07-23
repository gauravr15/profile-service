package com.odin.profileservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class ContactSnapshotUploadChunkV2Response {
    @JsonProperty("chunk_index")
    int chunkIndex;

    @JsonProperty("submitted_count")
    int submittedCount;

    @JsonProperty("canonical_count")
    int canonicalCount;

    @JsonProperty("expires_at")
    Instant expiresAt;
}
