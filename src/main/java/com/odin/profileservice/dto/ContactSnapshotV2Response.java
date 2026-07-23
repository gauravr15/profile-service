package com.odin.profileservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class ContactSnapshotV2Response {
    @JsonProperty("snapshot_id")
    String snapshotId;
    long revision;
    @JsonProperty("submitted_count")
    int submittedCount;
    @JsonProperty("canonical_count")
    int canonicalCount;
    @JsonProperty("added_count")
    int addedCount;
    @JsonProperty("removed_count")
    int removedCount;
    @JsonProperty("retained_count")
    int retainedCount;
    @JsonProperty("registered_count")
    int registeredCount;
    @JsonProperty("unregistered_count")
    int unregisteredCount;
    @JsonProperty("rejected_count")
    int rejectedCount;
    @JsonProperty("synced_at")
    Instant syncedAt;
}
