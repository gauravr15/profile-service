package com.odin.profileservice.service;

import com.odin.profileservice.entity.AccountDeletionOutbox;
import com.odin.profileservice.repo.AccountDeletionOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AccountDeletionOutboxClaimService {

    private final AccountDeletionOutboxRepository repository;

    @Transactional
    public Optional<AccountDeletionOutbox> claim(
            String eventId, String claimToken, Instant now, long claimSeconds) {
        Timestamp timestamp = Timestamp.from(now);
        int claimed = repository.claim(
                eventId,
                AccountDeletionOutbox.Status.PENDING,
                claimToken,
                timestamp,
                Timestamp.from(now.plusSeconds(claimSeconds)));
        return claimed == 1 ? repository.findById(eventId) : Optional.empty();
    }

    @Transactional
    public void markPublished(String eventId, String claimToken, Instant publishedAt) {
        if (repository.markPublished(
                eventId,
                AccountDeletionOutbox.Status.PENDING,
                AccountDeletionOutbox.Status.PUBLISHED,
                claimToken,
                Timestamp.from(publishedAt)) != 1) {
            throw new IllegalStateException("Outbox publication claim was lost");
        }
    }

    @Transactional
    public void releaseForRetry(
            AccountDeletionOutbox outbox,
            String claimToken,
            Instant nextAttemptAt,
            String errorCategory) {
        if (repository.releaseForRetry(
                outbox.getEventId(),
                AccountDeletionOutbox.Status.PENDING,
                claimToken,
                Timestamp.from(nextAttemptAt),
                errorCategory) != 1) {
            throw new IllegalStateException("Outbox retry claim was lost");
        }
    }
}
