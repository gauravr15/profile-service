package com.odin.profileservice.service;

import com.odin.profileservice.config.ContactDiscoveryProperties;
import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.CustomerDetailsDTO;
import com.odin.profileservice.dto.MobileListDTO;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.entity.BlockedContact;
import com.odin.profileservice.entity.Profile;
import com.odin.profileservice.entity.User;
import com.odin.profileservice.repo.BlockedContactRepository;
import com.odin.profileservice.repo.ProfileRepository;
import com.odin.profileservice.repo.UserRepository;
import com.odin.profileservice.utility.PhoneNumberHasher;
import com.odin.profileservice.utility.ResponseObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContactDiscoveryServiceTest {
    private ProfileRepository profiles;
    private BlockedContactRepository blocks;
    private ContactDiscoveryRateLimiter limiter;
    private ContactDiscoveryService service;

    @BeforeEach
    void setUp() {
        profiles = mock(ProfileRepository.class);
        UserRepository users = mock(UserRepository.class);
        blocks = mock(BlockedContactRepository.class);
        PhoneNumberHasher hasher = mock(PhoneNumberHasher.class);
        limiter = mock(ContactDiscoveryRateLimiter.class);
        ResponseObject responses = mock(ResponseObject.class);
        ContactDiscoveryProperties properties = new ContactDiscoveryProperties();
        SyncAuditService audit = mock(SyncAuditService.class);

        when(hasher.normalizePhoneNumber(anyString(), anyString()))
                .thenAnswer(invocation -> digits(invocation.getArgument(0)));
        when(hasher.hashWithGlobalPepper(anyString()))
                .thenAnswer(invocation -> "token-" + invocation.getArgument(0));
        when(users.findById("70")).thenReturn(Optional.of(
                User.builder().userId("70").globalPhoneHash("viewer-token").build()));
        when(blocks.findByBlockerUserIdAndBlockedGlobalPhoneHashIn(anyString(), anyCollection()))
                .thenReturn(Collections.emptyList());
        when(blocks.findByBlockerUserIdInAndBlockedGlobalPhoneHash(anyCollection(), anyString()))
                .thenReturn(Collections.emptyList());
        when(responses.buildResponse(anyString(), eq(ResponseCodes.SUCCESS_CODE), any()))
                .thenAnswer(invocation -> ResponseDTO.builder()
                        .statusCode(ResponseCodes.SUCCESS_CODE)
                        .status("SUCCESS")
                        .data(invocation.getArgument(2))
                        .build());
        when(responses.buildResponse(anyString(), eq(ResponseCodes.FAILURE_CODE)))
                .thenReturn(ResponseDTO.builder()
                        .statusCode(ResponseCodes.FAILURE_CODE)
                        .status("FAILURE")
                        .build());

        service = new ContactDiscoveryService(
                profiles, users, blocks, hasher, limiter, properties,
                responses, audit);
    }

    @Test
    void exactMatchPreservesCompatibleResponseAndDeduplicatesInput() {
        when(profiles.findLikeMobileNumber(anyList(), eq(true)))
                .thenReturn(List.of(activeProfile(92, "919900000092")));

        ResponseDTO result = service.discover("70", request(
                "919900000092", "+91 9900000092", "919900000092"));

        assertEquals(ResponseCodes.SUCCESS_CODE, result.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, CustomerDetailsDTO> data =
                (Map<String, CustomerDetailsDTO>) result.getData();
        assertEquals(1, data.size());
        CustomerDetailsDTO match = data.values().iterator().next();
        assertEquals(92, match.getCustomerId());
        assertEquals("First", match.getFirstName());
        assertEquals("Last", match.getLastName());
        assertEquals("919900000092", match.getMobile());
        assertNull(match.getEmail());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> numbers = ArgumentCaptor.forClass(List.class);
        verify(profiles).findLikeMobileNumber(numbers.capture(), eq(true));
        assertEquals(List.of("919900000092"), numbers.getValue());
        verify(limiter).enforce(eq("70"), eq(List.of("919900000092")));
    }

    @Test
    void partialPrefixSuffixAndDifferentCanonicalResultsNeverMatch() {
        when(profiles.findLikeMobileNumber(anyList(), eq(true)))
                .thenReturn(List.of(activeProfile(92, "919900000092")));

        assertEquals(ResponseCodes.FAILURE_CODE,
                service.discover("70", request("91990000009")).getStatusCode());
        assertEquals(ResponseCodes.FAILURE_CODE,
                service.discover("70", request("9900000092")).getStatusCode());
        assertEquals(ResponseCodes.FAILURE_CODE,
                service.discover("70", request("919900000093")).getStatusCode());
    }

    @Test
    void wildcardAndMalformedInputAreRejectedBeforeLookup() {
        assertInvalid(request("9199%000092"));
        assertInvalid(request("9199_000092"));
        assertInvalid(request("*919900000092"));
        verifyNoInteractions(profiles);
    }

    @Test
    void nullBlankOversizedAndInvalidCountryInputsAreRejected() {
        assertInvalid(null);
        assertInvalid(MobileListDTO.builder().countryCode("+91").build());
        assertInvalid(request((String) null));
        assertInvalid(request(" "));
        assertInvalid(request("1".repeat(33)));
        assertInvalid(MobileListDTO.builder()
                .mobile(List.of("919900000092")).countryCode("India").build());

        ContactDiscoveryProperties small = new ContactDiscoveryProperties();
        small.setMaxNumbers(1);
        ContactDiscoveryService bounded = new ContactDiscoveryService(
                profiles, mock(UserRepository.class), blocks,
                mock(PhoneNumberHasher.class), limiter, small,
                mock(ResponseObject.class), mock(SyncAuditService.class));
        assertThrows(ContactDiscoveryException.class,
                () -> bounded.discover("70", request("919900000092", "919900000093")));
    }

    @Test
    void inactiveDeletedAndInconsistentProfilesAreIndistinguishableFromNoMatch() {
        Profile inactive = activeProfile(91, "919900000091");
        inactive.setIsActive(false);
        Profile deleted = activeProfile(92, "919900000092");
        deleted.setIsDeleted(true);
        Profile inconsistent = activeProfile(null, "919900000093");
        when(profiles.findLikeMobileNumber(anyList(), eq(true)))
                .thenReturn(List.of(inactive, deleted, inconsistent));

        ResponseDTO result = service.discover("70", request(
                "919900000091", "919900000092", "919900000093"));

        assertEquals(ResponseCodes.FAILURE_CODE, result.getStatusCode());
    }

    @Test
    void requesterAndTargetBlocksBothSuppressTheMatchWithoutReasonLeak() {
        Profile target = activeProfile(92, "919900000092");
        when(profiles.findLikeMobileNumber(anyList(), eq(true)))
                .thenReturn(List.of(target));
        when(blocks.findByBlockerUserIdAndBlockedGlobalPhoneHashIn(
                eq("70"), anyCollection()))
                .thenReturn(List.of(BlockedContact.builder()
                        .blockerUserId("70")
                        .blockedGlobalPhoneHash("token-919900000092")
                        .build()));

        assertEquals(ResponseCodes.FAILURE_CODE,
                service.discover("70", request("919900000092")).getStatusCode());

        reset(blocks);
        when(blocks.findByBlockerUserIdAndBlockedGlobalPhoneHashIn(anyString(), anyCollection()))
                .thenReturn(Collections.emptyList());
        when(blocks.findByBlockerUserIdInAndBlockedGlobalPhoneHash(anyCollection(), anyString()))
                .thenReturn(List.of(BlockedContact.builder().blockerUserId("92").build()));
        assertEquals(ResponseCodes.FAILURE_CODE,
                service.discover("70", request("919900000092")).getStatusCode());
    }

    @Test
    void dependencyFailureIsRetryableAndNeverSuccessfulEmpty() {
        when(profiles.findLikeMobileNumber(anyList(), eq(true)))
                .thenThrow(new IllegalStateException("upstream unavailable"));

        ContactDiscoveryException error = assertThrows(
                ContactDiscoveryException.class,
                () -> service.discover("70", request("919900000092")));

        assertEquals("CONTACT_LOOKUP_UNAVAILABLE", error.getCode());
        assertEquals(503, error.getStatus().value());
    }

    @Test
    void oneNumberTapUsesSingleCategoryAndRemainsFunctional() {
        when(profiles.findLikeMobileNumber(anyList(), eq(true)))
                .thenReturn(List.of(activeProfile(92, "919900000092")));

        assertEquals(ResponseCodes.SUCCESS_CODE,
                service.discover("70", request("919900000092")).getStatusCode());
        verify(limiter).enforce("70", List.of("919900000092"));
    }

    private void assertInvalid(MobileListDTO request) {
        ContactDiscoveryException error = assertThrows(
                ContactDiscoveryException.class,
                () -> service.discover("70", request));
        assertEquals("CONTACT_LOOKUP_INVALID_REQUEST", error.getCode());
        assertEquals(400, error.getStatus().value());
    }

    private MobileListDTO request(String... numbers) {
        return MobileListDTO.builder()
                .mobile(numbers == null ? null : java.util.Arrays.asList(numbers))
                .countryCode("+91")
                .build();
    }

    private Profile activeProfile(Integer id, String mobile) {
        return Profile.builder()
                .customerId(id)
                .mobile(mobile)
                .firstName("First")
                .lastName("Last")
                .isActive(true)
                .isDeleted(false)
                .build();
    }

    private static String digits(String input) {
        return input.replaceAll("[^0-9]", "");
    }
}
