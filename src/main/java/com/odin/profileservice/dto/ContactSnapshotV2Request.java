package com.odin.profileservice.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class ContactSnapshotV2Request {

    @JsonProperty("snapshot_id")
    private String snapshotId;

    @JsonProperty("base_revision")
    private Long baseRevision;

    @JsonProperty("country_code")
    private String countryCode;

    @JsonProperty("contacts")
    private List<ContactItem> contacts;

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unknown V2 snapshot field");
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString(onlyExplicitlyIncluded = true)
    public static class ContactItem {

        @JsonProperty("phone_number")
        private String phoneNumber;

        @JsonAnySetter
        public void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("Unknown V2 contact field");
        }
    }
}
