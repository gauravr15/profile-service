package com.odin.profileservice.controller;

import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.dto.StatusEligibilityBatchRequest;
import com.odin.profileservice.dto.StatusEligibilityBatchResponse;
import com.odin.profileservice.service.StatusEligibilityDependencyException;
import com.odin.profileservice.service.StatusEligibilityRequestException;
import com.odin.profileservice.service.StatusEligibilityService;
import com.odin.profileservice.service.StatusVisibilityService;
import com.odin.profileservice.utility.ResponseObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for status visibility operations.
 * Called by multimedia service when a status is uploaded.
 */
@Slf4j
@RestController
@RequestMapping("/v1/status")
@RequiredArgsConstructor
public class StatusVisibilityController {

    private final StatusVisibilityService statusVisibilityService;
    private final StatusEligibilityService statusEligibilityService;
    private final ResponseObject responseObject;

    /**
     * Notify that a user has uploaded a status.
     * This will update the visibility lists for all eligible viewers in Redis.
     * 
     * Request body: { "customerId": "user123" }
     * 
     * @param request contains customerId of the uploader
     * @return success response
     */
    @PostMapping("/notify-upload")
    public ResponseEntity<ResponseDTO> notifyStatusUpload(@RequestBody Map<String, String> request) {
        String customerId = request.get("customerId");
        
        if (customerId == null || customerId.trim().isEmpty()) {
            log.warn("Received status upload notification with missing customerId");
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.INVALID_REQUEST);
            return ResponseEntity.ok(response);
        }

        log.info("Received status upload notification for customerId: {}", customerId);

        try {
            ResponseDTO response = statusVisibilityService.updateStatusVisibility(customerId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to process status upload notification for customerId: {}", customerId, e);
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.INTERNAL_SERVER_ERROR);
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Authoritative status eligibility contract for future feed and media access.
     * Viewer identity is supplied by the existing trusted gateway customerId header;
     * the request body cannot select a different viewer.
     */
    @PostMapping("/eligibility/batch")
    public ResponseEntity<ResponseDTO> evaluateStatusEligibility(
            @RequestHeader(value = "customerId", required = true) String viewerId,
            @RequestBody StatusEligibilityBatchRequest request) {
        try {
            StatusEligibilityBatchResponse result = statusEligibilityService
                    .evaluateViewerAgainstUploaders(viewerId,
                            request == null ? null : request.getUploaderIds());
            return ResponseEntity.ok(responseObject.buildResponse(ResponseCodes.SUCCESS_CODE, result));
        } catch (IllegalArgumentException invalidRequest) {
            log.warn("Invalid status eligibility request. viewerId={}", viewerId);
            return ResponseEntity.badRequest()
                    .body(responseObject.buildResponse(ResponseCodes.INVALID_REQUEST));
        } catch (StatusEligibilityRequestException deniedViewer) {
            log.warn("Status eligibility viewer rejected. viewerId={}, reason={}",
                    viewerId, deniedViewer.getReason());
			if (deniedViewer.getReason() == com.odin.profileservice.enums.StatusEligibilityReason.STATUS_REPAIR_REQUIRED) {
				return ResponseEntity.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
						.body(ResponseDTO.builder().statusCode(ResponseCodes.FAILURE_CODE)
								.status("REPAIR_REQUIRED").message("STATUS_PROFILE_REPAIR_REQUIRED").build());
			}
			return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
					.body(responseObject.buildResponse(ResponseCodes.FORBIDDEN));
        } catch (StatusEligibilityDependencyException dependencyFailure) {
            log.error("Status eligibility temporarily unavailable. viewerId={}", viewerId);
            return ResponseEntity.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
                    .body(responseObject.buildResponse(ResponseCodes.INTERNAL_SERVER_ERROR));
        }
    }
}
