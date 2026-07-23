package com.odin.profileservice.service;

import com.odin.profileservice.dto.ContactSnapshotUploadChunkV2Response;
import com.odin.profileservice.dto.ContactSnapshotUploadSessionV2Request;
import com.odin.profileservice.dto.ContactSnapshotUploadSessionV2Response;
import com.odin.profileservice.dto.ContactSnapshotV2Response;
import com.odin.profileservice.entity.ContactSnapshotRequestV2;
import com.odin.profileservice.entity.ContactSnapshotRequestV2Id;
import com.odin.profileservice.entity.ContactSnapshotOwnerLockV2;
import com.odin.profileservice.entity.ContactSnapshotUploadChunkV2;
import com.odin.profileservice.entity.ContactSnapshotUploadChunkV2Id;
import com.odin.profileservice.entity.ContactSnapshotUploadSessionV2;
import com.odin.profileservice.entity.ContactSnapshotUploadTargetV2;
import com.odin.profileservice.entity.Profile;
import com.odin.profileservice.repo.ContactSnapshotOwnerLockV2Repository;
import com.odin.profileservice.repo.ContactSnapshotRequestV2Repository;
import com.odin.profileservice.repo.ContactSnapshotUploadChunkV2Repository;
import com.odin.profileservice.repo.ContactSnapshotUploadSessionV2Repository;
import com.odin.profileservice.repo.ContactSnapshotUploadTargetV2Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContactSnapshotUploadTransactionV2Service {
    static final Duration SESSION_LIFETIME = Duration.ofHours(24);
    static final int MAX_OPEN_SESSIONS = 3;

    private final ContactSnapshotUploadSessionV2Repository sessionRepository;
    private final ContactSnapshotUploadChunkV2Repository chunkRepository;
    private final ContactSnapshotUploadTargetV2Repository targetRepository;
    private final ContactSnapshotOwnerLockV2Repository ownerLockRepository;
    private final ContactSnapshotRequestV2Repository requestRepository;
    private final ContactSnapshotV2Service snapshotService;
    private final ContactSnapshotV2CommitService commitService;
    private final ContactTokenService contactTokenService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public ContactSnapshotUploadSessionV2Response create(
            String ownerCustomerId,
            ContactSnapshotUploadSessionV2Request request,
            Instant now) {
        ContactSnapshotOwnerLockV2 ownerLock = ownerLockRepository.lockOwner(ownerCustomerId);
        if (ownerLock == null) {
            throw new IllegalStateException("Snapshot owner lock is unavailable");
        }
        ContactSnapshotUploadSessionV2 existing =
                sessionRepository.findByOwnerUserIdAndSnapshotId(
                        ownerCustomerId, request.getSnapshotId()).orElse(null);
        if (existing != null) {
            requireCurrentLifecycle(existing);
            if (existing.getState() == ContactSnapshotUploadSessionV2.State.OPEN
                    && !existing.getExpiresAt().toInstant().isAfter(now)) {
                deleteTemporary(existing.getSessionId());
                sessionRepository.delete(existing);
                sessionRepository.flush();
            } else {
                validateEquivalentCreation(existing, request);
                return response(existing);
            }
        }
        long activeSessions = sessionRepository.countByOwnerUserIdAndStateAndExpiresAtAfter(
                ownerCustomerId, ContactSnapshotUploadSessionV2.State.OPEN,
                Timestamp.from(now));
        if (activeSessions >= MAX_OPEN_SESSIONS) {
            throw new ContactSnapshotUploadV2Exception(
                    ContactSnapshotUploadV2Exception.Reason.SESSION_LIMIT);
        }
        Timestamp createdAt = Timestamp.from(now);
        ContactSnapshotUploadSessionV2 session = ContactSnapshotUploadSessionV2.builder()
                .sessionId(UUID.randomUUID().toString())
                .ownerUserId(ownerCustomerId)
                .snapshotId(request.getSnapshotId())
                .baseRevision(request.getBaseRevision())
                .countryCode(request.getCountryCode())
                .declaredTotalContacts(request.getTotalContacts())
                .declaredTotalChunks(request.getTotalChunks())
                .state(ContactSnapshotUploadSessionV2.State.OPEN)
                .expiresAt(Timestamp.from(now.plus(SESSION_LIFETIME)))
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .contactLifecycleEpoch(ownerLock.getContactLifecycleEpoch())
                .build();
        sessionRepository.saveAndFlush(session);
        return response(session);
    }

    @Transactional
    public ContactSnapshotUploadChunkV2Response uploadChunk(
            String ownerCustomerId,
            String sessionId,
            int chunkIndex,
            CanonicalContactSnapshotChunkV2 chunk,
            Instant now) {
        ContactSnapshotUploadSessionV2 session =
                findOpenOwnedSession(ownerCustomerId, sessionId, now);
        requireCurrentLifecycle(session);
        if (chunkIndex < 0 || chunkIndex >= session.getDeclaredTotalChunks()) {
            throw new ContactSnapshotUploadV2Exception(
                    ContactSnapshotUploadV2Exception.Reason.CHUNK_INDEX_OUT_OF_RANGE);
        }
        ContactSnapshotUploadChunkV2Id chunkId =
                new ContactSnapshotUploadChunkV2Id(sessionId, chunkIndex);
        ContactSnapshotUploadChunkV2 existing = chunkRepository.findById(chunkId).orElse(null);
        if (existing != null) {
            if (!existing.getPayloadDigest().equals(chunk.getPayloadDigest())) {
                throw new ContactSnapshotUploadV2Exception(
                        ContactSnapshotUploadV2Exception.Reason.CHUNK_INDEX_REUSE_CONFLICT);
            }
            return chunkResponse(existing, session.getExpiresAt().toInstant());
        }

        Timestamp createdAt = Timestamp.from(now);
        ContactSnapshotUploadChunkV2 storedChunk = ContactSnapshotUploadChunkV2.builder()
                .sessionId(sessionId)
                .chunkIndex(chunkIndex)
                .payloadDigest(chunk.getPayloadDigest())
                .submittedCount(chunk.getSubmittedCount())
                .canonicalCount(chunk.getTargetTokens().size())
                .createdAt(createdAt)
                .build();
        chunkRepository.save(storedChunk);
        mergeTargets(sessionId, chunk, createdAt);
        extendExpiry(session, now);
        chunkRepository.flush();
        targetRepository.flush();
        sessionRepository.flush();
        return chunkResponse(storedChunk, session.getExpiresAt().toInstant());
    }

    @Transactional
    public ContactSnapshotV2Response commit(
            String ownerCustomerId,
            String sessionId,
            Profile ownerProfile,
            Instant now) {
        ContactSnapshotUploadSessionV2 session =
                sessionRepository.findOwnedForUpdate(ownerCustomerId, sessionId)
                        .orElseThrow(() -> new ContactSnapshotUploadV2Exception(
                                ContactSnapshotUploadV2Exception.Reason.SESSION_NOT_FOUND));
        requireCurrentLifecycle(session);
        if (session.getState() == ContactSnapshotUploadSessionV2.State.COMMITTED) {
            return replayCommitted(session);
        }
        requireNotExpired(session, now);

        List<ContactSnapshotUploadChunkV2> chunks =
                chunkRepository.findBySessionIdOrderByChunkIndex(sessionId);
        validateComplete(session, chunks);
        List<ContactSnapshotUploadTargetV2> targets =
                targetRepository.findBySessionIdOrderByTargetGlobalPhoneHash(sessionId);
        if (targets.size() > ContactSnapshotUploadV2Service.MAX_TOTAL_CONTACTS) {
            throw new ContactSnapshotUploadV2Exception(
                    ContactSnapshotUploadV2Exception.Reason.SNAPSHOT_TOO_LARGE);
        }

        List<String> sortedTokens = targets.stream()
                .map(ContactSnapshotUploadTargetV2::getTargetGlobalPhoneHash)
                .collect(Collectors.toList());
        List<String> sortedCurrentTokens = targets.stream()
            .map(target -> target.getTargetGlobalPhoneToken() == null
                ? target.getTargetGlobalPhoneHash()
                : target.getTargetGlobalPhoneToken())
            .collect(Collectors.toList());
        int registeredCount = (int) targets.stream()
                .filter(target -> Boolean.TRUE.equals(target.getRegistered()))
                .count();
        CanonicalContactSnapshotV2 snapshot = snapshotService.prepareUploadedSnapshot(
                session.getSnapshotId(),
                session.getBaseRevision(),
                session.getCountryCode(),
                session.getDeclaredTotalContacts(),
                sortedTokens,
            sortedCurrentTokens,
                registeredCount,
                ownerProfile);
        ContactSnapshotV2Response committed = commitService.commit(ownerCustomerId, snapshot);

        Timestamp committedAt = Timestamp.from(now);
        session.setState(ContactSnapshotUploadSessionV2.State.COMMITTED);
        session.setPayloadDigest(snapshot.getPayloadDigest());
        session.setCommittedAt(committedAt);
        session.setUpdatedAt(committedAt);
        deleteTemporary(sessionId);
        sessionRepository.saveAndFlush(session);
        return committed;
    }

    @Transactional
    public void cancel(String ownerCustomerId, String sessionId) {
        ContactSnapshotUploadSessionV2 session =
                sessionRepository.findOwnedForUpdate(ownerCustomerId, sessionId).orElse(null);
        if (session == null) {
            return;
        }
        if (session.getState() == ContactSnapshotUploadSessionV2.State.COMMITTED) {
            throw new ContactSnapshotUploadV2Exception(
                    ContactSnapshotUploadV2Exception.Reason.SESSION_ALREADY_COMMITTED);
        }
        deleteTemporary(sessionId);
        sessionRepository.delete(session);
    }

    private ContactSnapshotUploadSessionV2 findOpenOwnedSession(
            String ownerCustomerId,
            String sessionId,
            Instant now) {
        ContactSnapshotUploadSessionV2 session =
                sessionRepository.findOwnedForUpdate(ownerCustomerId, sessionId)
                        .orElseThrow(() -> new ContactSnapshotUploadV2Exception(
                                ContactSnapshotUploadV2Exception.Reason.SESSION_NOT_FOUND));
        if (session.getState() == ContactSnapshotUploadSessionV2.State.COMMITTED) {
            throw new ContactSnapshotUploadV2Exception(
                    ContactSnapshotUploadV2Exception.Reason.SESSION_ALREADY_COMMITTED);
        }
        requireNotExpired(session, now);
        return session;
    }

    private void requireNotExpired(ContactSnapshotUploadSessionV2 session, Instant now) {
        if (!session.getExpiresAt().toInstant().isAfter(now)) {
            throw new ContactSnapshotUploadV2Exception(
                    ContactSnapshotUploadV2Exception.Reason.SESSION_EXPIRED);
        }
    }

    private void validateEquivalentCreation(
            ContactSnapshotUploadSessionV2 existing,
            ContactSnapshotUploadSessionV2Request request) {
        if (!existing.getBaseRevision().equals(request.getBaseRevision())
                || !existing.getCountryCode().equals(request.getCountryCode())
                || !existing.getDeclaredTotalContacts().equals(request.getTotalContacts())
                || !existing.getDeclaredTotalChunks().equals(request.getTotalChunks())) {
            throw new ContactSnapshotUploadV2Exception(
                    ContactSnapshotUploadV2Exception.Reason.SESSION_CONFLICT);
        }
    }

    private void validateComplete(
            ContactSnapshotUploadSessionV2 session,
            List<ContactSnapshotUploadChunkV2> chunks) {
        if (chunks.size() != session.getDeclaredTotalChunks()) {
            throw new ContactSnapshotUploadV2Exception(
                    ContactSnapshotUploadV2Exception.Reason.CHUNK_COUNT_INCOMPLETE);
        }
        int submitted = 0;
        for (int index = 0; index < chunks.size(); index++) {
            ContactSnapshotUploadChunkV2 chunk = chunks.get(index);
            if (chunk.getChunkIndex() != index) {
                throw new ContactSnapshotUploadV2Exception(
                        ContactSnapshotUploadV2Exception.Reason.CHUNK_COUNT_INCOMPLETE);
            }
            submitted += chunk.getSubmittedCount();
        }
        if (submitted != session.getDeclaredTotalContacts()) {
            throw new ContactSnapshotUploadV2Exception(
                    ContactSnapshotUploadV2Exception.Reason.DECLARED_TOTAL_MISMATCH);
        }
    }

    private void mergeTargets(
            String sessionId,
            CanonicalContactSnapshotChunkV2 chunk,
            Timestamp createdAt) {
        List<ContactSnapshotUploadTargetV2> existing =
                targetRepository.findExisting(sessionId, chunk.getTargetTokens());
        Map<String, ContactSnapshotUploadTargetV2> existingByToken = new HashMap<>();
        for (ContactSnapshotUploadTargetV2 target : existing) {
            existingByToken.put(target.getTargetGlobalPhoneHash(), target);
            if (chunk.getRegisteredTokens().contains(target.getTargetGlobalPhoneHash())) {
                target.setRegistered(true);
            }
        }
        List<ContactSnapshotUploadTargetV2> additions = new ArrayList<>();
        List<String> currentTokens = chunk.getTargetCurrentTokens();
        for (int index = 0; index < chunk.getTargetTokens().size(); index++) {
            String token = chunk.getTargetTokens().get(index);
            String currentToken = index < currentTokens.size() ? currentTokens.get(index) : token;
            if (!existingByToken.containsKey(token)) {
                additions.add(ContactSnapshotUploadTargetV2.builder()
                        .sessionId(sessionId)
                        .targetGlobalPhoneHash(token)
                        .targetGlobalPhoneToken(currentToken)
                        .targetGlobalPhoneTokenVersion(contactTokenService.getCurrentVersion())
                        .registered(chunk.getRegisteredTokens().contains(token))
                        .createdAt(createdAt)
                        .build());
            } else {
                ContactSnapshotUploadTargetV2 target = existingByToken.get(token);
                if (target.getTargetGlobalPhoneToken() == null) {
                    target.setTargetGlobalPhoneToken(currentToken);
                    target.setTargetGlobalPhoneTokenVersion(contactTokenService.getCurrentVersion());
                }
            }
        }
        for (ContactSnapshotUploadTargetV2 addition : additions) {
            entityManager.persist(addition);
        }
    }

    private ContactSnapshotV2Response replayCommitted(
            ContactSnapshotUploadSessionV2 session) {
        ContactSnapshotRequestV2 record = requestRepository.findById(
                new ContactSnapshotRequestV2Id(
                        session.getOwnerUserId(), session.getSnapshotId()))
                .orElseThrow(() -> new IllegalStateException(
                        "Committed upload session has no snapshot record"));
        return commitService.replayOrReject(
                record, session.getPayloadDigest(), session.getContactLifecycleEpoch());
    }

    private void extendExpiry(ContactSnapshotUploadSessionV2 session, Instant now) {
        session.setExpiresAt(Timestamp.from(now.plus(SESSION_LIFETIME)));
        session.setUpdatedAt(Timestamp.from(now));
        sessionRepository.save(session);
    }

    private void requireCurrentLifecycle(ContactSnapshotUploadSessionV2 session) {
        ContactSnapshotOwnerLockV2 ownerLock =
                ownerLockRepository.lockOwner(session.getOwnerUserId());
        if (ownerLock == null
                || !ownerLock.getContactLifecycleEpoch().equals(
                session.getContactLifecycleEpoch())) {
            throw new ContactSnapshotV2ReplayExpiredException();
        }
    }

    private void deleteTemporary(String sessionId) {
        targetRepository.deleteBySessionId(sessionId);
        chunkRepository.deleteBySessionId(sessionId);
    }

    private ContactSnapshotUploadSessionV2Response response(
            ContactSnapshotUploadSessionV2 session) {
        return ContactSnapshotUploadSessionV2Response.builder()
                .sessionId(session.getSessionId())
                .snapshotId(session.getSnapshotId())
                .baseRevision(session.getBaseRevision())
                .chunkSize(ContactSnapshotUploadV2Service.CHUNK_SIZE)
                .totalChunks(session.getDeclaredTotalChunks())
                .expiresAt(session.getExpiresAt().toInstant())
                .build();
    }

    private ContactSnapshotUploadChunkV2Response chunkResponse(
            ContactSnapshotUploadChunkV2 chunk,
            Instant expiresAt) {
        return ContactSnapshotUploadChunkV2Response.builder()
                .chunkIndex(chunk.getChunkIndex())
                .submittedCount(chunk.getSubmittedCount())
                .canonicalCount(chunk.getCanonicalCount())
                .expiresAt(expiresAt)
                .build();
    }
}
