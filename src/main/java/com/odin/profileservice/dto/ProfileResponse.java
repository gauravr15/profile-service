package com.odin.profileservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Profile response - returns user profile with privacy-filtered fields.
 * Fields are omitted (not null/placeholder) if permission is denied.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileResponse {

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("profile_photo_url")
    private String profilePhotoUrl;

    @JsonProperty("status")
    private String status;

    @JsonProperty("last_seen")
    private Long lastSeen;

    @JsonProperty("phone_hash_suffix")
    private String phoneHashSuffix;

    @JsonProperty("photo_version")
    private Integer photoVersion;
}
