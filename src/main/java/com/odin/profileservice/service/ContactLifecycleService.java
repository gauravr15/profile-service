package com.odin.profileservice.service;

import com.odin.profileservice.entity.ContactSnapshotOwnerLockV2;
import com.odin.profileservice.entity.ContactSnapshotRequestV2;
import com.odin.profileservice.repo.ContactRepository;
import com.odin.profileservice.repo.ContactSnapshotOwnerLockV2Repository;
import com.odin.profileservice.repo.ContactSnapshotRequestV2Repository;
import com.odin.profileservice.repo.ContactSnapshotUploadSessionV2Repository;
import com.odin.profileservice.repo.ContactSyncStateV2Repository;
import com.odin.profileservice.repo.ContactV2Repository;
import com.odin.profileservice.repo.SyncAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ContactLifecycleService {
    private final ContactSnapshotOwnerLockV2Repository ownerLockRepository;
    private final ContactRepository contactRepository;
    private final ContactV2Repository contactV2Repository;
    private final ContactSyncStateV2Repository syncStateRepository;
    private final ContactSnapshotRequestV2Repository requestRepository;
    private final ContactSnapshotUploadSessionV2Repository uploadSessionRepository;
    private final SyncAuditRepository syncAuditRepository;
    private final ContactDiscoveryRateLimiter discoveryRateLimiter;

    @Transactional
    public void deleteSyncedContacts(String ownerUserId) {
        ContactSnapshotOwnerLockV2 ownerLock = lockLifecycle(ownerUserId);
        advanceLifecycle(ownerLock);
        deleteOwnedState(ownerUserId);
    }

    @Transactional
    public void deleteAccountContacts(String ownerUserId, String targetGlobalPhoneHash) {
        ContactSnapshotOwnerLockV2 ownerLock = lockLifecycle(ownerUserId);
        advanceLifecycle(ownerLock);
        if (targetGlobalPhoneHash != null && !targetGlobalPhoneHash.isBlank()) {
            contactRepository.deleteAllByTargetGlobalPhoneHash(targetGlobalPhoneHash);
            contactV2Repository.deleteAllByTargetGlobalPhoneHash(targetGlobalPhoneHash);
        }
        deleteOwnedState(ownerUserId);
    }

    @Transactional
    public void removeReverseRelationshipsBeforePhoneChange(
            String ownerUserId,
            String oldTargetGlobalPhoneHash) {
        ContactSnapshotOwnerLockV2 ownerLock = lockLifecycle(ownerUserId);
        advanceLifecycle(ownerLock);
        if (oldTargetGlobalPhoneHash != null && !oldTargetGlobalPhoneHash.isBlank()) {
            contactRepository.deleteAllByTargetGlobalPhoneHash(oldTargetGlobalPhoneHash);
            contactV2Repository.deleteAllByTargetGlobalPhoneHash(oldTargetGlobalPhoneHash);
        }
    }

    /**
     * Mandatory preparation contract for any future authoritative phone-number
     * mutation. The caller must invoke this before persisting the new canonical
     * number and must fail the mutation if this method fails.
     */
    @Transactional
    public void prepareForPhoneNumberChange(
            String ownerUserId,
            String oldTargetGlobalPhoneHash) {
        removeReverseRelationshipsBeforePhoneChange(ownerUserId, oldTargetGlobalPhoneHash);
    }

    public void clearTemporaryDiscoveryState(String ownerUserId) {
        discoveryRateLimiter.clearAccountState(ownerUserId);
    }

    private ContactSnapshotOwnerLockV2 lockLifecycle(String ownerUserId) {
        if (!ownerLockRepository.existsById(ownerUserId)) {
            ownerLockRepository.saveAndFlush(ContactSnapshotOwnerLockV2.builder()
                    .ownerUserId(ownerUserId)
                    .createdAt(Timestamp.from(Instant.now()))
                    .contactLifecycleEpoch(0L)
                    .build());
        }
        ContactSnapshotOwnerLockV2 ownerLock = ownerLockRepository.lockOwner(ownerUserId);
        if (ownerLock == null) {
            throw new IllegalStateException("Contact lifecycle lock is unavailable");
        }
        return ownerLock;
    }

    private void advanceLifecycle(ContactSnapshotOwnerLockV2 ownerLock) {
        ownerLock.setContactLifecycleEpoch(ownerLock.getContactLifecycleEpoch() + 1);
        ownerLockRepository.save(ownerLock);
        Timestamp now = Timestamp.from(Instant.now());
        for (ContactSnapshotRequestV2 request
                : requestRepository.findByOwnerUserIdOrderByCreatedAtDesc(
                ownerLock.getOwnerUserId())) {
            request.setStatus(ContactSnapshotRequestV2.Status.TOMBSTONE);
            request.setSubmittedCount(null);
            request.setCanonicalCount(null);
            request.setAddedCount(null);
            request.setRemovedCount(null);
            request.setRetainedCount(null);
            request.setRegisteredCount(null);
            request.setUnregisteredCount(null);
            request.setRejectedCount(null);
            request.setSyncedAt(null);
            request.setUpdatedAt(now);
        }
    }

    private void deleteOwnedState(String ownerUserId) {
        contactRepository.deleteAllByOwnerUserId(ownerUserId);
        contactV2Repository.deleteAllByOwnerUserId(ownerUserId);
        syncStateRepository.deleteByOwnerUserId(ownerUserId);
        uploadSessionRepository.deleteAllByOwnerUserId(ownerUserId);
        if (syncAuditRepository.existsById(ownerUserId)) {
            syncAuditRepository.deleteById(ownerUserId);
        }
        ownerLockRepository.flush();
        requestRepository.flush();
        uploadSessionRepository.flush();
    }
}
