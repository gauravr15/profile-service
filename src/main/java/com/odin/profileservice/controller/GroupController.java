package com.odin.profileservice.controller;

import com.odin.profileservice.constants.ApplicationConstants;
import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.CreateGroupRequest;
import com.odin.profileservice.dto.GroupResponse;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.entity.Group;
import com.odin.profileservice.service.GroupService;
import com.odin.profileservice.utility.ResponseObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping(ApplicationConstants.API_VERSION)
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final ResponseObject responseObject;

    @PostMapping(ApplicationConstants.GROUPS)
    public ResponseEntity<ResponseDTO> createGroup(
            @RequestHeader(value = "customerId", required = true) String customerId,
            @RequestBody CreateGroupRequest request) {
        try {
            Group created = groupService.createGroup(customerId, request);
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.SUCCESS_CODE, toResponse(created));
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid create group request: {}", ex.getMessage());
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.INVALID_REQUEST);
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        } catch (Exception ex) {
            log.error("Failed to create group", ex);
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.INTERNAL_SERVER_ERROR);
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(ApplicationConstants.USERS + "/{customerId}" + ApplicationConstants.GROUPS)
    public ResponseEntity<ResponseDTO> getGroupsForUser(
            @PathVariable String customerId,
            @RequestHeader(value = "customerId", required = true) String callerId) {

        if (!customerId.equals(callerId)) {
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.FORBIDDEN);
            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }

        try {
            List<Group> groups = groupService.getGroupsForUser(customerId);
            List<GroupResponse> payload = groups.stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.SUCCESS_CODE, payload);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception ex) {
            log.error("Failed to fetch groups for user {}", customerId, ex);
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.INTERNAL_SERVER_ERROR);
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(ApplicationConstants.GROUPS + "/{groupId}")
    public ResponseEntity<ResponseDTO> getGroupById(
            @PathVariable String groupId,
            @RequestHeader(value = "customerId", required = false) String customerId) {
        try {
            if (StringUtils.hasText(customerId)) {
                Group group = groupService.getGroupForMember(groupId, customerId);
                ResponseDTO response = responseObject.buildResponse(ResponseCodes.SUCCESS_CODE, toResponse(group));
                return new ResponseEntity<>(response, HttpStatus.OK);
            }

            Group group = groupService.getGroup(groupId);
            Map<String, Object> payload = new HashMap<>();
            payload.put("groupId", group.getGroupId());
            payload.put("name", group.getName());
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.SUCCESS_CODE, payload);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (GroupService.GroupNotFoundException ex) {
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.NO_DATA_FOUND);
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        } catch (GroupService.GroupAccessDeniedException ex) {
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.FORBIDDEN);
            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        } catch (IllegalArgumentException ex) {
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.INVALID_REQUEST);
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        } catch (Exception ex) {
            log.error("Failed to fetch group {}", groupId, ex);
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.INTERNAL_SERVER_ERROR);
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(ApplicationConstants.GROUPS + "/{groupId}/members/{customerId}")
    public ResponseEntity<ResponseDTO> isUserMemberOfGroup(
            @PathVariable String groupId,
            @PathVariable String customerId) {
        try {
            boolean member = groupService.isMember(groupId, customerId);
            Map<String, Object> payload = new HashMap<>();
            payload.put("member", member);
            ResponseDTO response = responseObject.buildResponse(
                    member ? ResponseCodes.SUCCESS_CODE : ResponseCodes.NO_DATA_FOUND,
                    payload);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (IllegalArgumentException ex) {
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.INVALID_REQUEST);
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        } catch (Exception ex) {
            log.error("Failed to validate membership for group {} and customer {}", groupId, customerId, ex);
            ResponseDTO response = responseObject.buildResponse(ResponseCodes.INTERNAL_SERVER_ERROR);
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private GroupResponse toResponse(Group group) {
        String createdAt = group.getCreatedAt() != null ? group.getCreatedAt().toInstant().toString() : null;
        return GroupResponse.builder()
                .groupId(group.getGroupId())
                .name(group.getName())
            .members(group.getMembers() != null ? new java.util.ArrayList<>(group.getMembers()) : null)
            .admins(group.getAdmins() != null ? new java.util.ArrayList<>(group.getAdmins()) : null)
                .createdBy(group.getCreatedBy())
                .createdAt(createdAt)
                .avatarUrl(group.getAvatarUrl())
                .build();
    }
}
