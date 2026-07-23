package com.odin.profileservice.service;

import com.odin.profileservice.entity.ContactSnapshotUploadSessionV2;
import com.odin.profileservice.repo.ContactSnapshotUploadSessionV2Repository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContactSnapshotUploadCleanupV2 {
    static final int CLEANUP_BATCH_SIZE = 100;

    private final ContactSnapshotUploadSessionV2Repository sessionRepository;
    private final ContactSnapshotUploadCleanupWorkerV2 worker;

    @Scheduled(fixedDelayString =
            "${contact.snapshot.upload.cleanup-delay-ms:3600000}")
    public void removeExpiredSessions() {
        Instant started = Instant.now();
        int deleted = 0;
        int failures = 0;
        List<String> candidates = sessionRepository.findExpiredSessionIds(
                ContactSnapshotUploadSessionV2.State.OPEN,
                Timestamp.from(started),
                PageRequest.of(0, CLEANUP_BATCH_SIZE));
        for (String sessionId : candidates) {
            try {
                if (worker.deleteIfExpired(sessionId, started)) {
                    deleted++;
                }
            } catch (RuntimeException ex) {
                failures++;
            }
        }
        log.info("Contact snapshot upload cleanup candidates={} sessionsDeleted={} "
                        + "cleanupFailures={} cleanupDurationMs={}",
                candidates.size(), deleted, failures,
                Duration.between(started, Instant.now()).toMillis());
    }
}
