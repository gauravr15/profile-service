package com.odin.profileservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response for contact sync API.
 * Returns synced contacts and newly discovered registered users (no profile data).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactSyncResponse {

    @JsonProperty("synced_count")
    private int syncedCount;

    @JsonProperty("new_users")
    private List<NewUserInfo> newUsers;

    @JsonProperty("next_sync_token")
    private String nextSyncToken;

    @JsonProperty("sync_timestamp")
    private long syncTimestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NewUserInfo {
        @JsonProperty("is_registered")
        private boolean isRegistered;

        @JsonProperty("phone_hash_suffix")
        private String phoneHashSuffix;
    }
}
