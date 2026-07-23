package com.odin.profileservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odin.profileservice.dto.AccountDeletionEvent;
import com.odin.profileservice.entity.AccountDeletionOutbox;
import com.odin.profileservice.repo.AccountDeletionOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountDeletionOutboxService {

    static final String EVENT_TYPE = "ACCOUNT_DELETED";
    private static final String AGGREGATE_TYPE = "ACCOUNT";

    private final AccountDeletionOutboxRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public String enqueue(AccountDeletionEvent event) {
        String eventId = stableEventId(event.getCustomerId(), event.getGlobalPhoneHash());
        event.setEventId(eventId);
        // The reverse-contact hash has completed its local purpose and is not
        // required by the active downstream consumer.
        event.setGlobalPhoneHash(null);
        Timestamp now = Timestamp.from(Instant.now());
        try {
            repository.saveAndFlush(AccountDeletionOutbox.builder()
                    .eventId(eventId)
                    .aggregateType(AGGREGATE_TYPE)
                    .aggregateId(event.getCustomerId() + ":" + eventId)
                    .eventType(EVENT_TYPE)
                    .payload(objectMapper.writeValueAsString(event))
                    .status(AccountDeletionOutbox.Status.PENDING)
                    .attemptCount(0)
                    .nextAttemptAt(now)
                    .createdAt(now)
                    .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Account deletion event serialization failed", e);
        } catch (DataIntegrityViolationException duplicate) {
            if (!repository.existsById(eventId)) {
                throw duplicate;
            }
        }
        return eventId;
    }

    static String stableEventId(String customerId, String globalPhoneHash) {
        String lifecycleIdentity = customerId + ":" + String.valueOf(globalPhoneHash);
        return UUID.nameUUIDFromBytes(
                lifecycleIdentity.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
