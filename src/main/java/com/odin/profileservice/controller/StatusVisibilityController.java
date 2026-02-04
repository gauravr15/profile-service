package com.odin.profileservice.controller;

import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.ResponseDTO;
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
}
