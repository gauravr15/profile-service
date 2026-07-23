package com.odin.profileservice.controller;

import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.service.GroupService;
import com.odin.profileservice.utility.ResponseObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroupMembershipEndpointSecurityTest {

    private static final String GROUP_ID = "3f7b7cc1-0c31-4ef0-87bb-cd6317f3280a";
    private final GroupService groupService = mock(GroupService.class);
    private final ResponseObject responseObject = mock(ResponseObject.class);
    private final GroupController controller = new GroupController(groupService, responseObject);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "membershipServiceKey", "shared-secret");
        when(responseObject.buildResponse(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any())).thenAnswer(invocation ->
                ResponseDTO.builder()
                        .statusCode(invocation.getArgument(0))
                        .data(invocation.getArgument(1))
                        .build());
        when(responseObject.buildResponse(org.mockito.ArgumentMatchers.anyInt())).thenAnswer(invocation ->
                ResponseDTO.builder().statusCode(invocation.getArgument(0)).build());
    }

    @Test
    void authorizedCallerReceivesUnambiguousBoolean() {
        when(groupService.isMember(GROUP_ID, "70")).thenReturn(true);

        ResponseEntity<ResponseDTO> result =
                controller.isUserMemberOfGroup(GROUP_ID, "70", "shared-secret");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(Map.of("member", true), result.getBody().getData());
    }

    @Test
    void missingOrWrongCredentialCannotEnumerateMembership() {
        assertEquals(HttpStatus.FORBIDDEN,
                controller.isUserMemberOfGroup(GROUP_ID, "70", null).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
                controller.isUserMemberOfGroup(GROUP_ID, "70", "wrong").getStatusCode());
        verify(groupService, never()).isMember(GROUP_ID, "70");
    }

    @Test
    void missingServerCredentialFailsClosed() {
        ReflectionTestUtils.setField(controller, "membershipServiceKey", "");

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                controller.isUserMemberOfGroup(GROUP_ID, "70", "anything").getStatusCode());
    }
}
