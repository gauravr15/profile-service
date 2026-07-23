package com.odin.profileservice.service;

import com.odin.profileservice.entity.ContactSnapshotUploadSessionV2;
import com.odin.profileservice.repo.ContactSnapshotUploadChunkV2Repository;
import com.odin.profileservice.repo.ContactSnapshotUploadSessionV2Repository;
import com.odin.profileservice.repo.ContactSnapshotUploadTargetV2Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ContactSnapshotUploadCleanupWorkerV2 {
    private final ContactSnapshotUploadSessionV2Repository sessionRepository;
    private final ContactSnapshotUploadChunkV2Repository chunkRepository;
    private final ContactSnapshotUploadTargetV2Repository targetRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deleteIfExpired(String sessionId, Instant now) {
        ContactSnapshotUploadSessionV2 session =
                sessionRepository.findForUpdate(sessionId).orElse(null);
        if (session == null
                || session.getState() != ContactSnapshotUploadSessionV2.State.OPEN
                || session.getExpiresAt().toInstant().isAfter(now)) {
            return false;
        }
        targetRepository.deleteBySessionId(sessionId);
        chunkRepository.deleteBySessionId(sessionId);
        sessionRepository.delete(session);
        return true;
    }
}
