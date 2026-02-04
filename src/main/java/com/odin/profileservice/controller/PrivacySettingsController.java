package com.odin.profileservice.controller;

import com.odin.profileservice.dto.PrivacySettingsResponse;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.dto.UpdatePrivacySettingsRequest;
import com.odin.profileservice.entity.PrivacySettings;
import com.odin.profileservice.enums.PrivacyLevel;
import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.service.PrivacySettingsService;
import com.odin.profileservice.utility.ResponseObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Privacy settings management endpoint.
 * 
 * PUT /api/v1/settings/privacy
 */
@Slf4j
@RestController
@RequestMapping("/v1/settings")
@RequiredArgsConstructor
public class PrivacySettingsController {

    private final PrivacySettingsService privacySettingsService;
    private final ResponseObject responseObject;

    /**
     * Update user's privacy settings.
     * 
     * @param request new privacy levels
     * @param customerId customer ID from gateway header
     * @return updated settings
     */
    @PutMapping("/privacy")
    public ResponseEntity<ResponseDTO> updatePrivacySettings(
            @RequestBody UpdatePrivacySettingsRequest request,
            @RequestHeader(value = "customerId", required = true) String customerId) {

        if (customerId == null || customerId.trim().isEmpty()) {
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.INVALID_REQUEST);
            return ResponseEntity.ok(response);
        }

        String userId = customerId;

        try {
            // Parse privacy levels
            PrivacyLevel photoLevel = parsePrivacyLevel(request.getPhotoPrivacy());
            PrivacyLevel statusLevel = parsePrivacyLevel(request.getStatusPrivacy());
            PrivacyLevel lastSeenLevel = parsePrivacyLevel(request.getLastSeenPrivacy());

            // Update settings
            PrivacySettings updated = privacySettingsService.updateSettings(
                    userId, photoLevel, statusLevel, lastSeenLevel);

            // Build response
            PrivacySettingsResponse settingsResponse = PrivacySettingsResponse.builder()
                    .updated(true)
                    .settings(PrivacySettingsResponse.PrivacySettings.builder()
                            .photoPrivacy(updated.getPhotoPrivacy().name())
                            .statusPrivacy(updated.getStatusPrivacy().name())
                            .lastSeenPrivacy(updated.getLastSeenPrivacy().name())
                            .build())
                    .build();

            ResponseDTO response = responseObject.buildResponse(ResponseCodes.SUCCESS_CODE, settingsResponse);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid privacy level: {}", e.getMessage());
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.INVALID_REQUEST);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to update privacy settings", e);
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.INTERNAL_SERVER_ERROR);
            return ResponseEntity.ok(response);
        }
    }

    /**
    /**
     * Get current privacy settings.
     * 
     * @param customerId customer ID from gateway header
     * @return current settings
     */
    @GetMapping("/privacy")
    public ResponseEntity<ResponseDTO> getPrivacySettings(
            @RequestHeader(value = "customerId", required = true) String customerId) {

        if (customerId == null || customerId.trim().isEmpty()) {
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.INVALID_REQUEST);
            return ResponseEntity.ok(response);
        }

        String userId = customerId;
        try {
            PrivacySettings settings = privacySettingsService.getOrCreateSettings(userId);

            // If user doesn't exist in middleware yet, return default EVERYONE settings
            // (this can happen if privacy settings are queried before user is synced to middleware)
            if (settings == null) {
                log.debug("User {} not in middleware, returning default EVERYONE settings", userId);
                PrivacySettingsResponse settingsResponse = PrivacySettingsResponse.builder()
                        .updated(true)
                        .settings(PrivacySettingsResponse.PrivacySettings.builder()
                                .photoPrivacy(PrivacyLevel.EVERYONE.name())
                                .statusPrivacy(PrivacyLevel.EVERYONE.name())
                                .lastSeenPrivacy(PrivacyLevel.EVERYONE.name())
                                .build())
                        .build();
                ResponseDTO response = responseObject.buildResponse(ResponseCodes.SUCCESS_CODE, settingsResponse);
                return ResponseEntity.ok(response);
            }

            PrivacySettingsResponse settingsResponse = PrivacySettingsResponse.builder()
                    .updated(true)
                    .settings(PrivacySettingsResponse.PrivacySettings.builder()
                            .photoPrivacy(settings.getPhotoPrivacy().name())
                            .statusPrivacy(settings.getStatusPrivacy().name())
                            .lastSeenPrivacy(settings.getLastSeenPrivacy().name())
                            .build())
                    .build();

            ResponseDTO response = responseObject.buildResponse(ResponseCodes.SUCCESS_CODE, settingsResponse);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch privacy settings", e);
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.INTERNAL_SERVER_ERROR);
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Parse privacy level string (case-insensitive).
     */
    private PrivacyLevel parsePrivacyLevel(String level) {
        if (level == null || level.isEmpty()) {
            throw new IllegalArgumentException("Privacy level cannot be null");
        }
        try {
            return PrivacyLevel.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid privacy level: " + level);
        }
    }
}
