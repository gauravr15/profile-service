package com.odin.profileservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

/**
 * Request for contact sync API.
 * Contains list of phone numbers to sync (transmitted raw, hashed server-side).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class ContactSyncRequest {

    @JsonProperty("contacts")
    private List<ContactItem> contacts;

    @JsonProperty("country_code")
    private String countryCode;

    @JsonProperty("sync_token")
    private String syncToken;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString(onlyExplicitlyIncluded = true)
    public static class ContactItem {
        @JsonProperty("phone_number")
        private String phoneNumber;

        // NOTE: contact_name is NOT sent to server (client-side only)
        // This ensures privacy: server never receives contact labels

        @JsonProperty("last_synced")
        private long lastSynced;
    }
}
