package com.odin.profileservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.profileservice.dto.AccountDeletionEvent;
import com.odin.profileservice.entity.AccountDeletionOutbox;
import com.odin.profileservice.repo.AccountDeletionOutboxRepository;
import com.odin.profileservice.utility.AccountDeletionProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountDeletionOutboxPublisher {

    private final AccountDeletionOutboxRepository repository;
    private final AccountDeletionOutboxClaimService claimService;
    private final AccountDeletionProducer producer;
    private final ObjectMapper objectMapper;

    @Value("${app.account-deletion.outbox.batch-size:50}")
    private int batchSize;

    @Value("${app.account-deletion.outbox.claim-seconds:60}")
    private long claimSeconds;

    @Value("${app.account-deletion.outbox.max-backoff-seconds:300}")
    private long maxBackoffSeconds;

    @Scheduled(fixedDelayString = "${app.account-deletion.outbox.poll-delay-ms:5000}")
    public void publishPending() {
        Instant now = Instant.now();
        List<String> eventIds = repository.findPublishableEventIds(
                AccountDeletionOutbox.Status.PENDING,
                java.sql.Timestamp.from(now),
                PageRequest.of(0, Math.max(1, batchSize)));
        for (String eventId : eventIds) {
            publishClaimed(eventId, now);
        }
    }

    private void publishClaimed(String eventId, Instant now) {
        String claimToken = UUID.randomUUID().toString();
        claimService.claim(eventId, claimToken, now, claimSeconds)
                .ifPresent(outbox -> publish(outbox, claimToken));
    }

    private void publish(AccountDeletionOutbox outbox, String claimToken) {
        try {
            AccountDeletionEvent event =
                    objectMapper.readValue(outbox.getPayload(), AccountDeletionEvent.class);
            producer.publishAcknowledged(event);
            claimService.markPublished(outbox.getEventId(), claimToken, Instant.now());
            log.info("Account deletion outbox event published eventId={}", outbox.getEventId());
        } catch (Exception failure) {
            long delay = Math.min(
                    maxBackoffSeconds,
                    1L << Math.min(Math.max(outbox.getAttemptCount() - 1, 0), 8));
            String category = failure.getClass().getSimpleName();
            claimService.releaseForRetry(
                    outbox, claimToken, Instant.now().plusSeconds(delay), category);
            log.warn("Account deletion outbox publication deferred eventId={} category={}",
                    outbox.getEventId(), category);
        }
    }
}
