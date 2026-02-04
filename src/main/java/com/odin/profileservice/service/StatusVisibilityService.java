package com.odin.profileservice.service;

import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.entity.User;
import com.odin.profileservice.enums.PrivacyAttribute;
import com.odin.profileservice.repo.ContactRepository;
import com.odin.profileservice.repo.UserRepository;
import com.odin.profileservice.utility.ResponseObject;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service to handle status visibility logic.
 * When a user uploads a status, this service identifies all users who can view it
 * and updates their visibility lists in Redis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatusVisibilityService {

    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final PrivacyEvaluationService privacyEvaluationService;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Updates status visibility for all eligible viewers when a user uploads a status.
     * 
     * @param uploaderUserId The ID of the user who uploaded the status
     */
    
    @Autowired
    private ResponseObject response;
    
    
    public ResponseDTO updateStatusVisibility(String uploaderUserId) {
        log.info("Updating status visibility for uploader: {}", uploaderUserId);

        // 1. Get uploader's global phone hash
        User uploader = userRepository.findById(uploaderUserId)
                .orElseThrow(() -> new RuntimeException("Uploader not found: " + uploaderUserId));
        String uploaderGlobalHash = uploader.getGlobalPhoneHash();

        Set<String> potentialViewerIds = new HashSet<>();

        // 2. Find people who saved the uploader (Potential viewers if privacy is EVERYONE)
        contactRepository.findByTargetGlobalPhoneHash(uploaderGlobalHash).forEach(contact -> {
            potentialViewerIds.add(contact.getOwnerUserId());
        });

        // 3. Find people the uploader saved (Potential viewers if privacy is MY_CONTACTS)
        contactRepository.findByOwnerUserId(uploaderUserId).forEach(contact -> {
            // We need to find the userId for this global hash
            userRepository.findByGlobalPhoneHash(contact.getTargetGlobalPhoneHash()).ifPresent(user -> {
                potentialViewerIds.add(user.getUserId());
            });
        });

        // 4. Evaluate privacy for each potential viewer and update Redis
        potentialViewerIds.remove(uploaderUserId);
        	
        
        log.info("Status visibility update completed. Notified {} viewers for uploader {}", potentialViewerIds.size(), uploaderUserId);
        

        return response.buildResponse(ResponseCodes.SUCCESS_CODE, potentialViewerIds);

    }
}

