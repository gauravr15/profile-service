package com.odin.profileservice.service;

import com.odin.profileservice.entity.SyncAudit;
import com.odin.profileservice.repo.SyncAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncAuditService {

    private final SyncAuditRepository syncAuditRepository;

    /**
     * Get the last sync timestamp for a user.
     * If no record exists, create a new one with the current timestamp.
     * 
     * @param userId customer ID
     * @return the last synced timestamp
     */
    @Transactional
    public Timestamp getOrCreateLastSyncedTime(String userId) {
        return syncAuditRepository.findById(userId)
                .map(SyncAudit::getLastSyncedAt)
                .orElseGet(() -> {
                    Timestamp now = new Timestamp(System.currentTimeMillis());
                    SyncAudit audit = SyncAudit.builder()
                            .userId(userId)
                            .lastSyncedAt(now)
                            .build();
                    syncAuditRepository.save(audit);
                    log.info("Created initial sync audit for user: {}", userId);
                    return now;
                });
    }

    /**
     * Update or create the last sync timestamp for a user.
     * 
     * @param userId customer ID
     */
    @Transactional
    public void updateLastSyncedTime(String userId) {
        SyncAudit audit = syncAuditRepository.findById(userId)
                .orElse(SyncAudit.builder().userId(userId).build());
        
        audit.setLastSyncedAt(new Timestamp(System.currentTimeMillis()));
        syncAuditRepository.save(audit);
        log.info("Updated last synced time for user: {}", userId);
    }
}
