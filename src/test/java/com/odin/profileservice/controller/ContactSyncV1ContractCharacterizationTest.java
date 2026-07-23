package com.odin.profileservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.profileservice.constants.ResponseCodes;
import com.odin.profileservice.dto.ContactSyncRequest;
import com.odin.profileservice.dto.ContactSyncResponse;
import com.odin.profileservice.dto.ResponseDTO;
import com.odin.profileservice.entity.Profile;
import com.odin.profileservice.entity.User;
import com.odin.profileservice.repo.ProfileRepository;
import com.odin.profileservice.repo.UserRepository;
import com.odin.profileservice.service.ContactService;
import com.odin.profileservice.service.ContactTokenService;
import com.odin.profileservice.service.PrivacySettingsService;
import com.odin.profileservice.service.SyncAuditService;
import com.odin.profileservice.utility.PhoneNumberHasher;
import com.odin.profileservice.utility.ResponseObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterizes the existing V1 contract. These tests do not endorse V1 as
 * authoritative replacement semantics.
 */
class ContactSyncV1ContractCharacterizationTest {

    private final ContactService contactService = mock(ContactService.class);
    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PrivacySettingsService privacySettingsService = mock(PrivacySettingsService.class);
    private final PhoneNumberHasher phoneNumberHasher = mock(PhoneNumberHasher.class);
        private final ContactTokenService contactTokenService = mock(ContactTokenService.class);
    private final ResponseObject responseObject = mock(ResponseObject.class);
    private final SyncAuditService syncAuditService = mock(SyncAuditService.class);

    private ContactSyncController controller;

    @BeforeEach
    void setUp() {
        controller = new ContactSyncController(
                contactService,
                profileRepository,
                userRepository,
                privacySettingsService,
                phoneNumberHasher,
                contactTokenService,
                responseObject);
        ReflectionTestUtils.setField(controller, "sync", syncAuditService);

        Profile owner = Profile.builder()
                .customerId(59)
                .mobile("919900000059")
                .firstName("Owner")
                .build();
        when(profileRepository.findByCustomerId(59)).thenReturn(owner);
        when(phoneNumberHasher.hashWithGlobalPepper("919900000059")).thenReturn("owner-hash");
        when(userRepository.findById("59")).thenReturn(Optional.of(
                User.builder().userId("59").globalPhoneHash("owner-hash").build()));
        when(responseObject.buildResponse(eq(ResponseCodes.SUCCESS_CODE), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> ResponseDTO.builder()
                        .statusCode(ResponseCodes.SUCCESS_CODE)
                        .status("SUCCESS")
                        .data(invocation.getArgument(1))
                        .build());
    }

    @Test
    void dtoAcceptsCurrentSnakeCaseFieldsIncludingCurrentlyIgnoredMetadata() throws Exception {
        String json = "{\"contacts\":[{\"phone_number\":\"+919876543210\",\"last_synced\":42}],"
                + "\"country_code\":\"91\",\"sync_token\":\"legacy-token\"}";

        ContactSyncRequest request = new ObjectMapper().readValue(json, ContactSyncRequest.class);

        assertThat(request.getCountryCode()).isEqualTo("91");
        assertThat(request.getSyncToken()).isEqualTo("legacy-token");
        assertThat(request.getContacts()).singleElement().satisfies(contact -> {
            assertThat(contact.getPhoneNumber()).isEqualTo("+919876543210");
            assertThat(contact.getLastSynced()).isEqualTo(42);
        });
    }

    @Test
    void explicitEmptyListSucceedsAndUpdatesAuditWithoutDeletingContacts() {
        ContactSyncRequest request = ContactSyncRequest.builder()
                .contacts(Collections.emptyList())
                .countryCode("91")
                .syncToken("ignored-token")
                .build();

        ResponseEntity<ResponseDTO> result = controller.syncContacts(request, "59");

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatusCode()).isEqualTo(ResponseCodes.SUCCESS_CODE);
        ContactSyncResponse payload = (ContactSyncResponse) result.getBody().getData();
        assertThat(payload.getSyncedCount()).isZero();
        assertThat(payload.getNewUsers()).isEmpty();
        assertThat(payload.getNextSyncToken()).isNotBlank();
        verify(syncAuditService).updateLastSyncedTime("59");
        verify(contactService, never()).deleteContact(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void duplicateEntriesAreProcessedTwiceWhilePersistenceRemainsIdempotent() {
        ContactSyncRequest.ContactItem duplicate = ContactSyncRequest.ContactItem.builder()
                .phoneNumber("+919876543210")
                .lastSynced(7)
                .build();
        ContactSyncRequest request = ContactSyncRequest.builder()
                .contacts(List.of(duplicate, duplicate))
                .countryCode("91")
                .syncToken("ignored-token")
                .build();
        when(profileRepository.findLikeMobileNumber(anyList(), eq(true)))
                .thenReturn(Collections.emptyList());
        when(contactService.saveContact("59", "+919876543210", "", "IN"))
                .thenReturn(true);

        ResponseEntity<ResponseDTO> result = controller.syncContacts(request, "59");

        ContactSyncResponse payload = (ContactSyncResponse) result.getBody().getData();
        assertThat(payload.getSyncedCount()).isEqualTo(2);
        verify(contactService, times(2)).saveContact("59", "+919876543210", "", "IN");
        verify(syncAuditService).updateLastSyncedTime("59");
    }
}
