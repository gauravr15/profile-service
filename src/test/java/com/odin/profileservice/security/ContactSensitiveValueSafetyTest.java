package com.odin.profileservice.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.odin.profileservice.dto.ContactSyncRequest;
import com.odin.profileservice.dto.CustomerDetailsDTO;
import com.odin.profileservice.dto.MobileListDTO;
import com.odin.profileservice.repo.ContactRepository;
import com.odin.profileservice.repo.UserRepository;
import com.odin.profileservice.service.ContactService;
import com.odin.profileservice.service.ContactTokenService;
import com.odin.profileservice.service.PrivacyEvaluationService;
import com.odin.profileservice.utility.PhoneNumberHasher;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class ContactSensitiveValueSafetyTest {

    private static final String SYNTHETIC_PHONE = "15550001111";

    @Test
    void phoneNormalizationExceptionDoesNotContainInput() {
        PhoneNumberHasher hasher = new PhoneNumberHasher();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> hasher.normalizePhoneNumber(SYNTHETIC_PHONE + "999999999999999999", "US"));

        assertFalse(failure.getMessage().contains(SYNTHETIC_PHONE));
    }

    @Test
    void contactServiceDoesNotLogPhoneBearingExceptionMessage() {
        PhoneNumberHasher hasher = mock(PhoneNumberHasher.class);
        when(hasher.normalizePhoneNumber(anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("bad " + SYNTHETIC_PHONE));
        ContactService service = new ContactService(mock(ContactRepository.class),
            mock(UserRepository.class), hasher, mock(ContactTokenService.class),
            mock(PrivacyEvaluationService.class));
        ListAppender<ILoggingEvent> appender = capture(ContactService.class);
        try {
            service.saveContact("70", SYNTHETIC_PHONE, "", "US");
        } finally {
            detach(ContactService.class, appender);
        }

        assertFalse(messages(appender).contains(SYNTHETIC_PHONE));
    }

    @Test
    void contactDtosDoNotExposeSensitiveFieldsThroughToString() {
        MobileListDTO bulk = MobileListDTO.builder()
                .mobile(Collections.singletonList(SYNTHETIC_PHONE)).countryCode("1").build();
        ContactSyncRequest sync = ContactSyncRequest.builder()
                .contacts(Collections.singletonList(ContactSyncRequest.ContactItem.builder()
                        .phoneNumber(SYNTHETIC_PHONE).lastSynced(0).build()))
                .countryCode("1").build();
        CustomerDetailsDTO result = CustomerDetailsDTO.builder()
                .customerId(70).mobile(SYNTHETIC_PHONE).firstName("SyntheticName").build();

        String rendered = bulk + " " + sync + " " + result;
        assertFalse(rendered.contains(SYNTHETIC_PHONE));
        assertFalse(rendered.contains("SyntheticName"));
        assertFalse(rendered.contains("70"));
    }

    private ListAppender<ILoggingEvent> capture(Class<?> type) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(type)).addAppender(appender);
        return appender;
    }

    private void detach(Class<?> type, ListAppender<ILoggingEvent> appender) {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(type)).detachAppender(appender);
    }

    private String messages(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
    }
}
