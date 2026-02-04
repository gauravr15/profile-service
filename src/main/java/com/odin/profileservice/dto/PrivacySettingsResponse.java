package com.odin.profileservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for privacy settings update.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivacySettingsResponse {

    @JsonProperty("updated")
    private boolean updated;

    @JsonProperty("settings")
    private PrivacySettings settings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrivacySettings {
        @JsonProperty("photo_privacy")
        private String photoPrivacy;

        @JsonProperty("status_privacy")
        private String statusPrivacy;

        @JsonProperty("last_seen_privacy")
        private String lastSeenPrivacy;
    }
}
