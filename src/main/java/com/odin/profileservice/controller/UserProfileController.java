package com.odin.profileservice.controller;

import com.odin.profileservice.dto.ProfileResponse;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.entity.User;
import com.odin.profileservice.enums.PrivacyAttribute;
import com.odin.profileservice.repo.UserRepository;
import com.odin.profileservice.service.PrivacyEvaluationService;
import com.odin.profileservice.service.StatusVisibilityService;
import com.odin.profileservice.utility.ResponseObject;
import com.odin.profileservice.constants.ResponseCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Profile endpoint with privacy filtering.
 * Returns user profile data, omitting fields that fail privacy checks.
 * 
 * GET /api/v1/profiles/{target_user_id}
 */
@Slf4j
@RestController
@RequestMapping("/v1/profiles")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserRepository userRepository;
    private final PrivacyEvaluationService privacyEvaluationService;
    private final StatusVisibilityService statusVisibilityService;
    private final ResponseObject responseObject;

    /**
     * Get user profile with privacy-filtered fields.
     * 
     * @param targetUserId user being viewed
     * @param customerId customer ID from gateway header
     * @return profile data with restricted fields omitted
     */
    @GetMapping("/{targetUserId}")
    public ResponseEntity<ResponseDTO> getProfile(
            @PathVariable String targetUserId,
            @RequestHeader(value = "customerId", required = true) String customerId) {

        if (customerId == null || customerId.trim().isEmpty()) {
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.INVALID_REQUEST);
            return ResponseEntity.ok(response);
        }

        String viewerUserId = customerId;

        try {
            // Fetch target user
            Optional<User> targetOpt = userRepository.findById(targetUserId);
            if (!targetOpt.isPresent()) {
                ResponseDTO response = responseObject.buildResponse(ResponseCodes.NO_DATA_FOUND);
                return ResponseEntity.ok(response);
            }

            User target = targetOpt.get();

            // Evaluate privacy for each attribute
            boolean canViewPhoto = privacyEvaluationService.canViewAttribute(viewerUserId, targetUserId, PrivacyAttribute.PHOTO);
            boolean canViewStatus = privacyEvaluationService.canViewAttribute(viewerUserId, targetUserId, PrivacyAttribute.STATUS);
            boolean canViewLastSeen = privacyEvaluationService.canViewAttribute(viewerUserId, targetUserId, PrivacyAttribute.LAST_SEEN);

            // Build response with privacy-filtered fields
            ProfileResponse profileResponse = ProfileResponse.builder()
                    .userId(targetUserId)
                    .displayName(target.getDisplayName())
                    .phoneHashSuffix(getPhoneHashSuffix(target.getPhoneHash()))
                    .photoVersion(target.getPhotoVersion())
                    .build();

            // Only include fields if permitted
            if (canViewPhoto) {
                profileResponse.setProfilePhotoUrl(target.getProfilePhotoUrl());
            }
            if (canViewStatus) {
                profileResponse.setStatus(target.getStatusText());
            }
            if (canViewLastSeen) {
                profileResponse.setLastSeen(target.getLastSeenTimestamp());
            }

            ResponseDTO response = responseObject.buildResponse(ResponseCodes.SUCCESS_CODE, profileResponse);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch profile", e);
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.INTERNAL_SERVER_ERROR);
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Increment photo version for the given user.
     * Called by multimedia-service after a profile photo upload.
     *
     * @param customerId customer ID from gateway header
     * @return updated photo version
     */
    @PostMapping("/photo-version/increment")
    public ResponseEntity<ResponseDTO> incrementPhotoVersion(
            @RequestBody Map<String, String> requestBody) {

        String customerId = requestBody.get("customerId");
        if (customerId == null || customerId.trim().isEmpty()) {
            return ResponseEntity.ok(responseObject.buildResponse(ResponseCodes.INVALID_REQUEST));
        }

        try {
            Optional<User> userOpt = userRepository.findById(customerId);
            if (!userOpt.isPresent()) {
                return ResponseEntity.ok(responseObject.buildResponse(ResponseCodes.NO_DATA_FOUND));
            }

            User user = userOpt.get();
            int newVersion = (user.getPhotoVersion() != null ? user.getPhotoVersion() : 0) + 1;
            user.setPhotoVersion(newVersion);
            userRepository.save(user);

            Map<String, Object> result = new HashMap<>();
            result.put("photoVersion", newVersion);
            result.put("customerId", customerId);

            return ResponseEntity.ok(responseObject.buildResponse(ResponseCodes.SUCCESS_CODE, result));
        } catch (Exception e) {
            log.error("Failed to increment photo version for customerId={}", customerId, e);
            return ResponseEntity.ok(responseObject.buildResponse(ResponseCodes.INTERNAL_SERVER_ERROR));
        }
    }

    /**
     * Fan-out endpoint: returns the set of user IDs allowed to see this user's profile photo updates.
     * Reuses StatusVisibilityService logic for contact fan-out + privacy evaluation.
     *
     * @param requestBody must contain "customerId" of the photo uploader
     * @return set of viewer customer IDs
     */
    @PostMapping("/photo/notify-upload")
    public ResponseEntity<ResponseDTO> notifyPhotoUpload(@RequestBody Map<String, String> requestBody) {
        String uploaderUserId = requestBody.get("customerId");
        if (uploaderUserId == null || uploaderUserId.trim().isEmpty()) {
            return ResponseEntity.ok(responseObject.buildResponse(ResponseCodes.INVALID_REQUEST));
        }

        try {
            ResponseDTO visibilityResult = statusVisibilityService.updateStatusVisibility(uploaderUserId);
            return ResponseEntity.ok(visibilityResult);
        } catch (Exception e) {
            log.error("Failed to compute photo notification fan-out for customerId={}", uploaderUserId, e);
            return ResponseEntity.ok(responseObject.buildResponse(ResponseCodes.INTERNAL_SERVER_ERROR));
        }
    }

    /**
     * Get last 4 characters of phone hash for UX (no security risk).
     */
    private String getPhoneHashSuffix(String phoneHash) {
        if (phoneHash == null || phoneHash.length() < 4) {
            return "****";
        }
        return "..." + phoneHash.substring(phoneHash.length() - 4);
    }
}
