package com.odin.profileservice.controller;

import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.entity.User;
import com.odin.profileservice.repo.ContactRepository;
import com.odin.profileservice.repo.UserRepository;
import com.odin.profileservice.utility.ResponseObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Contact status endpoint - check if phone numbers are registered and if I have saved them.
 * 
 * GET /v1/contacts/status?phone_hashes=hash1,hash2,hash3
 */
@Slf4j
@RestController
@RequestMapping("/v1/contacts")
@RequiredArgsConstructor
public class ContactStatusController {

    private final UserRepository userRepository;
    private final ContactRepository contactRepository;
    private final ResponseObject responseObject;

    /**
     * Get status of contacts (registered, saved by me).
     * 
     * @param phoneHashes comma-separated list of phone hashes
     * @param customerId customer ID from gateway header
     * @return contact status info
     */
    @GetMapping("/status")
    public ResponseEntity<ResponseDTO> getContactStatus(
            @RequestParam String phoneHashes,
            @RequestHeader(value = "customerId", required = true) String customerId) {

        if (customerId == null || customerId.trim().isEmpty()) {
            return ResponseEntity.ok(responseObject.buildResponse(ResponseCodes.INVALID_REQUEST));
        }

        String userId = customerId;

        try {
            // Parse phone hashes
            String[] hashes = phoneHashes.split(",");
            if (hashes.length > 100) {
                return ResponseEntity.ok(responseObject.buildResponse(ResponseCodes.INVALID_REQUEST, "Maximum 100 phone hashes allowed"));
            }

            List<ContactStatusInfo> results = new ArrayList<>();

            for (String hash : hashes) {
                String trimmedHash = hash.trim();

                // Check if registered
                Optional<User> userOpt = userRepository.findByPhoneHash(trimmedHash);
                boolean isRegistered = userOpt.isPresent();

                // Check if saved by me
                boolean isSavedByMe = contactRepository
                        .existsByOwnerUserIdAndTargetGlobalPhoneHash(userId, trimmedHash);

                ContactStatusInfo info = ContactStatusInfo.builder()
                        .phoneHash(trimmedHash)
                        .userId(isRegistered ? userOpt.get().getUserId() : null)
                        .isRegistered(isRegistered)
                        .isSavedByMe(isSavedByMe)
                        .displayName(isRegistered ? userOpt.get().getDisplayName() : null)
                        .build();

                results.add(info);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("contacts", results);
            return ResponseEntity.ok(responseObject.buildResponse(ResponseCodes.SUCCESS_CODE, data));

        } catch (Exception e) {
            log.error("Failed to get contact status", e);
            return ResponseEntity.ok(responseObject.buildResponse(ResponseCodes.INTERNAL_SERVER_ERROR));
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContactStatusInfo {
        private String phoneHash;
        private String userId;
        private boolean isRegistered;
        private boolean isSavedByMe;
        private String displayName;
    }
}
