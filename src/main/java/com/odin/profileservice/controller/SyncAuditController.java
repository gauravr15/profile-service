package com.odin.profileservice.controller;

import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.service.SyncAuditService;
import com.odin.profileservice.utility.ResponseObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for tracking contact sync audits.
 */
@Slf4j
@RestController
@RequestMapping("/v1/sync-audit")
@RequiredArgsConstructor
public class SyncAuditController {

    private final SyncAuditService syncAuditService;
    private final ResponseObject responseObject;

    /**
     * Get last synced date time for a customer.
     * 
     * @param customerId the ID of the customer
     * @return last synced date time
     */
    @GetMapping("/{customerId}")
    public ResponseEntity<ResponseDTO> getLastSyncedTime(@PathVariable String customerId) {
        log.info("Fetching last synced time for customerId: {}", customerId);
        
        if (customerId == null || customerId.trim().isEmpty()) {
            return ResponseEntity.ok(responseObject.buildResponse(ResponseCodes.INVALID_REQUEST, "Missing customerId"));
        }

        try {
            Timestamp lastSynced = syncAuditService.getOrCreateLastSyncedTime(customerId);
            
            Map<String, Object> data = new HashMap<>();
            data.put("customerId", customerId);
            data.put("lastSyncedAt", lastSynced);
            return ResponseEntity.ok(responseObject.buildResponse(ResponseCodes.SUCCESS_CODE, data));
        } catch (Exception e) {
            log.error("Error retrieving last synced time for customer {}: {}", customerId, e.getMessage());
            return ResponseEntity.ok(responseObject.buildResponse(ResponseCodes.INTERNAL_SERVER_ERROR));
        }
    }

    /**
     * Update last synced date time for a customer.
     * 
     * @param customerId the ID of the customer
     * @return success response
     */
    @PostMapping("/{customerId}")
    public ResponseEntity<ResponseDTO> updateLastSyncedTime(@PathVariable String customerId) {
        log.info("Updating last synced time for customerId: {}", customerId);

        if (customerId == null || customerId.trim().isEmpty()) {
            return ResponseEntity.ok(responseObject.buildResponse(ResponseCodes.INVALID_REQUEST, "Missing customerId"));
        }

        try {
            syncAuditService.updateLastSyncedTime(customerId);
            return ResponseEntity.ok(responseObject.buildResponse(ResponseCodes.SUCCESS_CODE, "Sync time updated successfully"));
        } catch (Exception e) {
            log.error("Error updating last synced time for customer {}: {}", customerId, e.getMessage());
            return ResponseEntity.ok(responseObject.buildResponse(ResponseCodes.INTERNAL_SERVER_ERROR));
        }
    }
}
