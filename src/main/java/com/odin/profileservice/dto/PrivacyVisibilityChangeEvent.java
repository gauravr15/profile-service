package com.odin.profileservice.dto;

import com.odin.profileservice.enums.PrivacyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Kafka event published when a user's privacy settings change.
 * Contains eligible contacts to notify via FCM.
 * Consumed by notification-service to send PRIVACY_CHANGE FCM notifications.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivacyVisibilityChangeEvent {

    private String userId;
    
    // New privacy levels after change
    private String photoPrivacy;
    private String lastSeenPrivacy;
    
    // Old privacy levels before change (for context)
    private String oldPhotoPrivacy;
    private String oldLastSeenPrivacy;
    
    // Contacts eligible to receive FCM notification
    private List<String> eligibleContactIds;
    
    private long timestamp;
}
