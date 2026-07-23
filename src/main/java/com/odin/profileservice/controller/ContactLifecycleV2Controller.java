package com.odin.profileservice.controller;

import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.service.ContactLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/v2/contacts")
@RequiredArgsConstructor
public class ContactLifecycleV2Controller {
    private final ContactLifecycleService contactLifecycleService;

    @DeleteMapping
    public ResponseEntity<ResponseDTO> deleteSyncedContacts(
            @RequestHeader("customerId") String customerId) {
        contactLifecycleService.deleteSyncedContacts(customerId);
        contactLifecycleService.clearTemporaryDiscoveryState(customerId);
        return ResponseEntity.ok(ResponseDTO.builder()
                .statusCode(ResponseCodes.SUCCESS_CODE)
                .status(ResponseCodes.SUCCESS)
                .data(Map.of(
                        "status", "deleted",
                        "deleted_at", Instant.now().toString()))
                .build());
    }
}
