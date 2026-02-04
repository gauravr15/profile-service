package com.odin.profileservice.controller;

import com.odin.profileservice.dto.PrivacyCheckResponse;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.enums.PrivacyAttribute;
import com.odin.profileservice.service.PrivacyEvaluationService;
import com.odin.profileservice.utility.ResponseObject;
import com.odin.profileservice.constants.ResponseCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Privacy check endpoint.
 * Messaging Service calls this to verify if a user can view profile attributes.
 * 
 * GET /api/v1/profiles/{target_user_id}/privacy-check?attributes=photo,status,last_seen
 */
@Slf4j
@RestController
@RequestMapping("/v1/profiles")
@RequiredArgsConstructor
public class PrivacyCheckController {

    private final PrivacyEvaluationService privacyEvaluationService;
    private final ResponseObject responseObject;

    /**
     * Check if viewer can see target's attributes.
     * 
     * @param targetUserId user being viewed
     * @param attributes CSV list of attributes to check (photo, status, last_seen)
     * @param customerId customer ID from gateway header
     * @return privacy check result
     */
    @GetMapping("/{targetUserId}/privacy-check")
    public ResponseEntity<ResponseDTO> checkPrivacy(
            @PathVariable String targetUserId,
            @RequestParam(required = false) String attributes,
            @RequestHeader(value = "customerId", required = true) String customerId) {

        if (customerId == null || customerId.trim().isEmpty()) {
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.INVALID_REQUEST);
            return ResponseEntity.ok(response);
        }

        String viewerUserId = customerId;

        try {
            // Parse requested attributes (default to all)
            java.util.Set<PrivacyAttribute> requestedAttrs;
            if (attributes == null || attributes.isEmpty()) {
                requestedAttrs = new java.util.HashSet<>(Arrays.asList(PrivacyAttribute.values()));
            } else {
                requestedAttrs = Arrays.stream(attributes.split(","))
                        .map(String::trim)
                        .map(PrivacyAttribute::fromString)
                        .collect(Collectors.toSet());
            }

            // Evaluate privacy for each attribute
            Map<String, Boolean> canView = new HashMap<>();
            for (PrivacyAttribute attr : requestedAttrs) {
                boolean allowed = privacyEvaluationService.canViewAttribute(viewerUserId, targetUserId, attr);
                canView.put(attr.getValue(), allowed);
            }

            // Build response
            PrivacyCheckResponse privacyCheckResponse = PrivacyCheckResponse.builder()
                    .canView(canView)
                    .targetUserId(targetUserId)
                    .timestamp(System.currentTimeMillis())
                    .build();

            ResponseDTO response = responseObject.buildResponse(ResponseCodes.SUCCESS_CODE, privacyCheckResponse);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid attributes parameter: {}", attributes, e);
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.INVALID_REQUEST);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Privacy check failed", e);
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.INTERNAL_SERVER_ERROR);
            return ResponseEntity.ok(response);
        }
    }
}
