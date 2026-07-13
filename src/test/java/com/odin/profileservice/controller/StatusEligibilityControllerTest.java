package com.odin.profileservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.dto.StatusEligibilityBatchResponse;
import com.odin.profileservice.dto.StatusEligibilityResult;
import com.odin.profileservice.enums.StatusEligibilityDecision;
import com.odin.profileservice.enums.StatusEligibilityReason;
import com.odin.profileservice.service.StatusEligibilityService;
import com.odin.profileservice.service.StatusVisibilityService;
import com.odin.profileservice.utility.ResponseObject;

class StatusEligibilityControllerTest {

    private MockMvc mvc;
    private StatusEligibilityService eligibilityService;
    private ResponseObject responseObject;

    @BeforeEach
    void setUp() {
        eligibilityService = mock(StatusEligibilityService.class);
        responseObject = mock(ResponseObject.class);
        StatusVisibilityController controller = new StatusVisibilityController(
                mock(StatusVisibilityService.class), eligibilityService, responseObject);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void usesHeaderViewerAndReturnsOnlySafeDecisionFields() throws Exception {
        StatusEligibilityBatchResponse payload = new StatusEligibilityBatchResponse("1",
                Collections.singletonList(new StatusEligibilityResult("2", StatusEligibilityDecision.ALLOW,
                        StatusEligibilityReason.ALLOWED_FORWARD_CONTACT)));
        when(eligibilityService.evaluateViewerAgainstUploaders(eq("1"), any())).thenReturn(payload);
        when(responseObject.buildResponse(ResponseCodes.SUCCESS_CODE, payload)).thenReturn(
                ResponseDTO.builder().statusCode(ResponseCodes.SUCCESS_CODE).status("SUCCESS").data(payload).build());

        mvc.perform(post("/v1/status/eligibility/batch").header("customerId", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"viewerId\":\"999\",\"uploaderIds\":[\"2\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewerId").value("1"))
                .andExpect(jsonPath("$.data.results[0].uploaderId").value("2"))
                .andExpect(jsonPath("$.data.results[0].decision").value("ALLOW"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hash"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("phone"))));
    }

    @Test
    void missingViewerIdentityIsRejected() throws Exception {
        mvc.perform(post("/v1/status/eligibility/batch")
                .contentType(MediaType.APPLICATION_JSON).content("{\"uploaderIds\":[\"2\"]}"))
                .andExpect(status().isBadRequest());
    }
}
