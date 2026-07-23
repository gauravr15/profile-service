package com.odin.profileservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.profileservice.dto.AccountDeletionEvent;
import com.odin.profileservice.entity.AccountDeletionOutbox;
import com.odin.profileservice.repo.AccountDeletionOutboxRepository;
import com.odin.profileservice.utility.AccountDeletionProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountDeletionOutboxPublisherTest {

    private AccountDeletionOutboxRepository repository;
    private AccountDeletionOutboxClaimService claimService;
    private AccountDeletionProducer producer;
    private AccountDeletionOutboxPublisher publisher;
    private AccountDeletionOutbox outbox;

    @BeforeEach
    void setUp() throws Exception {
        repository = mock(AccountDeletionOutboxRepository.class);
        claimService = mock(AccountDeletionOutboxClaimService.class);
        producer = mock(AccountDeletionProducer.class);
        outbox = pending();
        publisher = new AccountDeletionOutboxPublisher(
                repository, claimService, producer, new ObjectMapper());
        ReflectionTestUtils.setField(publisher, "batchSize", 2);
        ReflectionTestUtils.setField(publisher, "claimSeconds", 60L);
        ReflectionTestUtils.setField(publisher, "maxBackoffSeconds", 300L);
        when(repository.findPublishableEventIds(any(), any(), any(Pageable.class)))
                .thenReturn(List.of("event-a"));
        when(claimService.claim(anyString(), anyString(), any(), anyLong()))
                .thenReturn(Optional.of(outbox));
    }

    @Test
    void acknowledgedSendMarksPublishedAndUsesStableEventId() throws Exception {
        publisher.publishPending();

        ArgumentCaptor<AccountDeletionEvent> event =
                ArgumentCaptor.forClass(AccountDeletionEvent.class);
        verify(producer).publishAcknowledged(event.capture());
        assertThat(event.getValue().getEventId()).isEqualTo("event-a");
        verify(claimService).markPublished(
                anyString(), anyString(), any(Instant.class));
        verify(claimService, never()).releaseForRetry(
                any(), anyString(), any(), anyString());
    }

    @Test
    void failedSendRemainsPendingAndIsScheduledForRetry() throws Exception {
        doThrow(new IllegalStateException("broker unavailable"))
                .when(producer).publishAcknowledged(any());

        publisher.publishPending();

        verify(claimService, never()).markPublished(
                anyString(), anyString(), any());
        verify(claimService).releaseForRetry(
                any(), anyString(), any(Instant.class),
                org.mockito.ArgumentMatchers.eq("IllegalStateException"));
    }

    @Test
    void workerUsesConfiguredBoundedBatch() {
        publisher.publishPending();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findPublishableEventIds(
                any(), any(Timestamp.class), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
    }

    private AccountDeletionOutbox pending() throws Exception {
        AccountDeletionEvent event = AccountDeletionEvent.builder()
                .eventId("event-a")
                .customerId("59")
                .timestamp(100L)
                .contactOwnerIds(List.of("70"))
                .build();
        Timestamp now = Timestamp.from(Instant.now());
        return AccountDeletionOutbox.builder()
                .eventId("event-a")
                .aggregateType("ACCOUNT")
                .aggregateId("59:event-a")
                .eventType("ACCOUNT_DELETED")
                .payload(new ObjectMapper().writeValueAsString(event))
                .status(AccountDeletionOutbox.Status.PENDING)
                .attemptCount(1)
                .nextAttemptAt(now)
                .createdAt(now)
                .build();
    }
}
