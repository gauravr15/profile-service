package com.odin.profileservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request for updating privacy settings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePrivacySettingsRequest {

    @JsonProperty("photo_privacy")
    private String photoPrivacy;

    @JsonProperty("status_privacy")
    private String statusPrivacy;

    @JsonProperty("last_seen_privacy")
    private String lastSeenPrivacy;
}
