package com.odin.profileservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response for privacy check API.
 * Returns visibility status for requested attributes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivacyCheckResponse {

    @JsonProperty("can_view")
    private Map<String, Boolean> canView;

    @JsonProperty("viewer_phone_hash_suffix")
    private String viewerPhoneHashSuffix;

    @JsonProperty("target_user_id")
    private String targetUserId;

    @JsonProperty("timestamp")
    private long timestamp;
}
