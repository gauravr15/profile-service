package com.odin.profileservice.controller;

import com.odin.profileservice.dto.ProfileResponse;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.entity.User;
import com.odin.profileservice.enums.PrivacyAttribute;
import com.odin.profileservice.repo.UserRepository;
import com.odin.profileservice.service.PrivacyEvaluationService;
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
     * Get last 4 characters of phone hash for UX (no security risk).
     */
    private String getPhoneHashSuffix(String phoneHash) {
        if (phoneHash == null || phoneHash.length() < 4) {
            return "****";
        }
        return "..." + phoneHash.substring(phoneHash.length() - 4);
    }
}
