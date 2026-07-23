package com.odin.profileservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = false)
public class ContactSnapshotUploadSessionV2Request {
    @JsonProperty("snapshot_id")
    private String snapshotId;

    @JsonProperty("base_revision")
    private Long baseRevision;

    @JsonProperty("country_code")
    private String countryCode;

    @JsonProperty("total_contacts")
    private Integer totalContacts;

    @JsonProperty("total_chunks")
    private Integer totalChunks;
}
