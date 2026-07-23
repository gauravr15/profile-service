package com.odin.profileservice.service;

import com.odin.profileservice.dto.ContactSnapshotUploadChunkV2Request;
import com.odin.profileservice.dto.ContactSnapshotUploadChunkV2Response;
import com.odin.profileservice.dto.ContactSnapshotUploadSessionV2Request;
import com.odin.profileservice.dto.ContactSnapshotUploadSessionV2Response;
import com.odin.profileservice.dto.ContactSnapshotV2Response;
import com.odin.profileservice.entity.ContactSnapshotUploadSessionV2;
import com.odin.profileservice.entity.Profile;
import com.odin.profileservice.repo.ContactSnapshotUploadSessionV2Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContactSnapshotUploadV2Service {
    public static final int CHUNK_SIZE = 500;
    public static final int MAX_TOTAL_CONTACTS = 10_000;
    public static final int MAX_TOTAL_CHUNKS = 20;

    private final ContactSnapshotUploadSessionV2Repository sessionRepository;
    private final ContactSnapshotV2Service snapshotService;
    private final ContactSnapshotUploadTransactionV2Service transactionService;

    public ContactSnapshotUploadSessionV2Response create(
            String ownerCustomerId,
            ContactSnapshotUploadSessionV2Request request) {
        ContactSnapshotUploadSessionV2Request canonicalRequest = validateCreation(request);
        snapshotService.validateOwner(ownerCustomerId);
        snapshotService.ensureOwnerLock(ownerCustomerId);
        return transactionService.create(ownerCustomerId, canonicalRequest, Instant.now());
    }

    public ContactSnapshotUploadChunkV2Response uploadChunk(
            String ownerCustomerId,
            String sessionId,
            int chunkIndex,
            ContactSnapshotUploadChunkV2Request request) {
        String canonicalSessionId = canonicalUuid(sessionId);
        ContactSnapshotUploadSessionV2 session = ownedSession(ownerCustomerId, canonicalSessionId);
        if (request == null || request.getContacts() == null || request.getContacts().isEmpty()) {
            throw new ContactSnapshotUploadV2Exception(
                    ContactSnapshotUploadV2Exception.Reason.CHUNK_VALIDATION_FAILED);
        }
        if (request.getContacts().size() > CHUNK_SIZE) {
            throw new ContactSnapshotUploadV2Exception(
                    ContactSnapshotUploadV2Exception.Reason.SNAPSHOT_TOO_LARGE);
        }
        final CanonicalContactSnapshotChunkV2 chunk;
        try {
            chunk = snapshotService.canonicalizeChunk(
                    session.getCountryCode(), request.getContacts(), CHUNK_SIZE);
        } catch (ContactSnapshotV2ValidationException ex) {
            throw new ContactSnapshotUploadV2Exception(
                    ContactSnapshotUploadV2Exception.Reason.CHUNK_VALIDATION_FAILED);
        }
        return transactionService.uploadChunk(
                ownerCustomerId, canonicalSessionId, chunkIndex, chunk, Instant.now());
    }

    public ContactSnapshotV2Response commit(String ownerCustomerId, String sessionId) {
        String canonicalSessionId = canonicalUuid(sessionId);
        ownedSession(ownerCustomerId, canonicalSessionId);
        Profile ownerProfile = snapshotService.validateOwner(ownerCustomerId);
        return transactionService.commit(
                ownerCustomerId, canonicalSessionId, ownerProfile, Instant.now());
    }

    public void cancel(String ownerCustomerId, String sessionId) {
        transactionService.cancel(ownerCustomerId, canonicalUuid(sessionId));
    }

    private ContactSnapshotUploadSessionV2Request validateCreation(
            ContactSnapshotUploadSessionV2Request request) {
        if (request == null) {
            throw validationFailure();
        }
        ContactSnapshotUploadSessionV2Request canonical = canonicalizeCreation(request);
        if (canonical.getBaseRevision() == null || canonical.getBaseRevision() < 0
                || canonical.getTotalContacts() == null
                || canonical.getTotalContacts() <= 0
                || canonical.getTotalChunks() == null
                || canonical.getTotalChunks() <= 0
                || canonical.getTotalChunks()
                != requiredChunks(canonical.getTotalContacts())) {
            throw validationFailure();
        }
        if (canonical.getTotalContacts() > MAX_TOTAL_CONTACTS
                || canonical.getTotalChunks() > MAX_TOTAL_CHUNKS) {
            throw new ContactSnapshotUploadV2Exception(
                    ContactSnapshotUploadV2Exception.Reason.SNAPSHOT_TOO_LARGE);
        }
        return canonical;
    }

    private ContactSnapshotUploadSessionV2Request canonicalizeCreation(
            ContactSnapshotUploadSessionV2Request request) {
        try {
            String snapshotId = snapshotService.validateSnapshotId(request.getSnapshotId());
            String countryCode = snapshotService.validateCountryCode(request.getCountryCode());
            return ContactSnapshotUploadSessionV2Request.builder()
                    .snapshotId(snapshotId)
                    .baseRevision(request.getBaseRevision())
                    .countryCode(countryCode)
                    .totalContacts(request.getTotalContacts())
                    .totalChunks(request.getTotalChunks())
                    .build();
        } catch (ContactSnapshotV2ValidationException ex) {
            throw validationFailure();
        }
    }

    private int requiredChunks(int totalContacts) {
        return (totalContacts + CHUNK_SIZE - 1) / CHUNK_SIZE;
    }

    private ContactSnapshotUploadSessionV2 ownedSession(
            String ownerCustomerId,
            String sessionId) {
        ContactSnapshotUploadSessionV2 session =
                sessionRepository.findById(sessionId).orElse(null);
        if (session == null || !session.getOwnerUserId().equals(ownerCustomerId)) {
            throw new ContactSnapshotUploadV2Exception(
                    ContactSnapshotUploadV2Exception.Reason.SESSION_NOT_FOUND);
        }
        return session;
    }

    private String canonicalUuid(String value) {
        if (value == null || value.length() != 36) {
            throw new ContactSnapshotUploadV2Exception(
                    ContactSnapshotUploadV2Exception.Reason.SESSION_NOT_FOUND);
        }
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException();
            }
            return parsed.toString();
        } catch (IllegalArgumentException ex) {
            throw new ContactSnapshotUploadV2Exception(
                    ContactSnapshotUploadV2Exception.Reason.SESSION_NOT_FOUND);
        }
    }

    private ContactSnapshotUploadV2Exception validationFailure() {
        return new ContactSnapshotUploadV2Exception(
                ContactSnapshotUploadV2Exception.Reason.CHUNK_VALIDATION_FAILED);
    }
}
