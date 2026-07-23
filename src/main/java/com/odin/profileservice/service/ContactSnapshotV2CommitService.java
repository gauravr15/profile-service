package com.odin.profileservice.service;

import com.odin.profileservice.dto.ContactSnapshotV2Response;
import com.odin.profileservice.entity.ContactSnapshotRequestV2;
import com.odin.profileservice.entity.ContactSnapshotOwnerLockV2;
import com.odin.profileservice.entity.ContactSyncStateV2;
import com.odin.profileservice.entity.ContactV2;
import com.odin.profileservice.entity.User;
import com.odin.profileservice.repo.ContactSnapshotOwnerLockV2Repository;
import com.odin.profileservice.repo.ContactSnapshotRequestV2Repository;
import com.odin.profileservice.repo.ContactSyncStateV2Repository;
import com.odin.profileservice.repo.ContactV2Repository;
import com.odin.profileservice.repo.UserRepository;
import com.odin.profileservice.utility.PhoneNumberHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContactSnapshotV2CommitService {
    static final int FULL_REPLAY_RECORD_LIMIT = 100;
    static final Duration FULL_REPLAY_RETENTION = Duration.ofDays(90);

    private final ContactSnapshotOwnerLockV2Repository ownerLockRepository;
    private final ContactSnapshotRequestV2Repository requestRepository;
    private final ContactSyncStateV2Repository stateRepository;
    private final ContactV2Repository contactRepository;
    private final UserRepository userRepository;
    private final PhoneNumberHasher phoneNumberHasher;
    private final ContactTokenService contactTokenService;
    private final PrivacySettingsService privacySettingsService;
    private final SyncAuditService syncAuditService;
    private final PrivacyEvaluationService privacyEvaluationService;

    @Transactional
    public ContactSnapshotV2Response commit(
            String ownerCustomerId,
            CanonicalContactSnapshotV2 snapshot) {
        ContactSnapshotOwnerLockV2 ownerLock = ownerLockRepository.lockOwner(ownerCustomerId);
        if (ownerLock == null) {
            throw new IllegalStateException("Snapshot owner lock is unavailable");
        }
        long lifecycleEpoch = ownerLock.getContactLifecycleEpoch();

        ContactSnapshotRequestV2 prior = requestRepository.findForUpdate(
                ownerCustomerId, snapshot.getSnapshotId()).orElse(null);
        if (prior != null) {
            return replayOrReject(prior, snapshot.getPayloadDigest(), lifecycleEpoch);
        }

        ContactSyncStateV2 state = stateRepository.findById(ownerCustomerId).orElse(null);
        long currentRevision = state == null ? 0 : state.getCurrentRevision();
        if (currentRevision != snapshot.getBaseRevision()) {
            throw new ContactSnapshotV2RevisionException(currentRevision);
        }
        ensureMiddlewareOwner(ownerCustomerId, snapshot);

        List<ContactV2> existing = contactRepository.findByOwnerUserId(ownerCustomerId);
        Map<String, ContactV2> existingByToken = new HashMap<>();
        for (ContactV2 contact : existing) {
            existingByToken.put(contact.getTargetGlobalPhoneHash(), contact);
        }

        Set<String> existingTokens = existingByToken.keySet();
        Set<String> incomingTokens = new HashSet<>(snapshot.getTargetTokens());
        Map<String, String> currentTokenByLegacy = mapCurrentTokens(snapshot);
        Set<String> toAdd = difference(incomingTokens, existingTokens);
        Set<String> toRemove = difference(existingTokens, incomingTokens);
        Set<String> retained = intersection(existingTokens, incomingTokens);

        List<ContactV2> additions = toAdd.stream()
            .map(token -> ContactV2.builder()
                .ownerUserId(ownerCustomerId)
                .targetGlobalPhoneHash(token)
                .targetGlobalPhoneToken(currentTokenByLegacy.getOrDefault(token, token))
                .targetGlobalPhoneTokenVersion(2)
                .build())
                .collect(Collectors.toList());
        List<ContactV2> removals = toRemove.stream()
                .map(existingByToken::get)
                .collect(Collectors.toList());
        contactRepository.saveAll(additions);
        contactRepository.deleteAll(removals);

        long nextRevision = currentRevision + 1;
        Timestamp now = Timestamp.from(Instant.now());
        if (state == null) {
            state = ContactSyncStateV2.builder().ownerUserId(ownerCustomerId).build();
        }
        state.setCurrentRevision(nextRevision);
        state.setLastSnapshotId(snapshot.getSnapshotId());
        state.setLastSyncedAt(now);
        stateRepository.save(state);
        syncAuditService.updateLastSyncedTime(ownerCustomerId);

        ContactSnapshotV2Response response = ContactSnapshotV2Response.builder()
                .snapshotId(snapshot.getSnapshotId())
                .revision(nextRevision)
                .submittedCount(snapshot.getSubmittedCount())
                .canonicalCount(snapshot.getTargetTokens().size())
                .addedCount(toAdd.size())
                .removedCount(toRemove.size())
                .retainedCount(retained.size())
                .registeredCount(snapshot.getRegisteredCount())
                .unregisteredCount(snapshot.getTargetTokens().size() - snapshot.getRegisteredCount())
                .rejectedCount(0)
                .syncedAt(now.toInstant())
                .build();
        requestRepository.save(toRecord(
                ownerCustomerId, snapshot, response, now, lifecycleEpoch));

        contactRepository.flush();
        stateRepository.flush();
        requestRepository.flush();
        compactReplayMetadata(ownerCustomerId, snapshot.getSnapshotId(), now.toInstant());

        for (String changedToken : union(toAdd, toRemove)) {
            privacyEvaluationService.invalidateContactCache(ownerCustomerId, changedToken);
        }
        return response;
    }

    ContactSnapshotV2Response replayOrReject(
            ContactSnapshotRequestV2 record,
            String payloadDigest,
            long currentLifecycleEpoch) {
        if (!record.getPayloadDigest().equals(payloadDigest)) {
            throw new ContactSnapshotV2ConflictException();
        }
        if (record.getStatus() != ContactSnapshotRequestV2.Status.COMMITTED
                || record.getContactLifecycleEpoch() != currentLifecycleEpoch) {
            throw new ContactSnapshotV2ReplayExpiredException();
        }
        return ContactSnapshotV2Response.builder()
                .snapshotId(record.getSnapshotId())
                .revision(record.getCommittedRevision())
                .submittedCount(record.getSubmittedCount())
                .canonicalCount(record.getCanonicalCount())
                .addedCount(record.getAddedCount())
                .removedCount(record.getRemovedCount())
                .retainedCount(record.getRetainedCount())
                .registeredCount(record.getRegisteredCount())
                .unregisteredCount(record.getUnregisteredCount())
                .rejectedCount(record.getRejectedCount())
                .syncedAt(record.getSyncedAt().toInstant())
                .build();
    }

    private ContactSnapshotRequestV2 toRecord(
            String ownerCustomerId,
            CanonicalContactSnapshotV2 snapshot,
            ContactSnapshotV2Response response,
            Timestamp now,
            long lifecycleEpoch) {
        return ContactSnapshotRequestV2.builder()
                .ownerUserId(ownerCustomerId)
                .snapshotId(snapshot.getSnapshotId())
                .payloadDigest(snapshot.getPayloadDigest())
                .baseRevision(snapshot.getBaseRevision())
                .committedRevision(response.getRevision())
                .status(ContactSnapshotRequestV2.Status.COMMITTED)
                .submittedCount(response.getSubmittedCount())
                .canonicalCount(response.getCanonicalCount())
                .addedCount(response.getAddedCount())
                .removedCount(response.getRemovedCount())
                .retainedCount(response.getRetainedCount())
                .registeredCount(response.getRegisteredCount())
                .unregisteredCount(response.getUnregisteredCount())
                .rejectedCount(response.getRejectedCount())
                .syncedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .contactLifecycleEpoch(lifecycleEpoch)
                .build();
    }

    private void compactReplayMetadata(
            String ownerCustomerId,
            String latestSnapshotId,
            Instant now) {
        Timestamp cutoff = Timestamp.from(now.minus(FULL_REPLAY_RETENTION));
        List<ContactSnapshotRequestV2> records =
                requestRepository.findByOwnerUserIdOrderByCreatedAtDesc(ownerCustomerId);
        List<ContactSnapshotRequestV2> compacted = new ArrayList<>();
        for (int index = FULL_REPLAY_RECORD_LIMIT; index < records.size(); index++) {
            ContactSnapshotRequestV2 record = records.get(index);
            if (record.getCreatedAt().before(cutoff)
                    && !record.getSnapshotId().equals(latestSnapshotId)
                    && record.getStatus() == ContactSnapshotRequestV2.Status.COMMITTED) {
                record.setStatus(ContactSnapshotRequestV2.Status.TOMBSTONE);
                record.setSubmittedCount(null);
                record.setCanonicalCount(null);
                record.setAddedCount(null);
                record.setRemovedCount(null);
                record.setRetainedCount(null);
                record.setRegisteredCount(null);
                record.setUnregisteredCount(null);
                record.setRejectedCount(null);
                record.setSyncedAt(null);
                record.setUpdatedAt(Timestamp.from(now));
                compacted.add(record);
            }
        }
        requestRepository.saveAll(compacted);
    }

    private void ensureMiddlewareOwner(
            String ownerCustomerId,
            CanonicalContactSnapshotV2 snapshot) {
        if (userRepository.findById(ownerCustomerId).isPresent()) {
            privacySettingsService.getOrCreateSettings(ownerCustomerId);
            return;
        }
        String normalized = snapshot.getOwnerProfile().getMobile().replaceAll("[^0-9]", "");
        String salt = phoneNumberHasher.generateSalt();
        ContactTokenService.LookupTokenMaterial tokenMaterial =
                contactTokenService.deriveLookupTokensFromCanonical(normalized);
        User owner = User.builder()
                .userId(ownerCustomerId)
                .phoneHash(phoneNumberHasher.hashPhoneNumber(normalized, salt))
                .phoneToken(tokenMaterial.getCurrentToken())
                .phoneTokenVersion(tokenMaterial.getCurrentVersion())
                .phoneSalt(salt)
                .globalPhoneHash(phoneNumberHasher.hashWithGlobalPepper(normalized))
                .globalPhoneToken(tokenMaterial.getCurrentToken())
                .globalPhoneTokenVersion(tokenMaterial.getCurrentVersion())
                .pepperVersion(1)
                .displayName(((snapshot.getOwnerProfile().getFirstName() == null
                        ? "" : snapshot.getOwnerProfile().getFirstName())
                        + " " + (snapshot.getOwnerProfile().getLastName() == null
                        ? "" : snapshot.getOwnerProfile().getLastName())).trim())
                .build();
        userRepository.save(owner);
        privacySettingsService.getOrCreateSettings(ownerCustomerId);
    }

    private Map<String, String> mapCurrentTokens(CanonicalContactSnapshotV2 snapshot) {
        Map<String, String> currentTokenByLegacy = new HashMap<>();
        List<String> legacyTokens = snapshot.getTargetTokens();
        List<String> currentTokens = snapshot.getTargetCurrentTokens();
        for (int index = 0; index < legacyTokens.size(); index++) {
            String legacy = legacyTokens.get(index);
            String current = index < currentTokens.size() ? currentTokens.get(index) : legacy;
            currentTokenByLegacy.put(legacy, current);
        }
        return currentTokenByLegacy;
    }

    private Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new HashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private Set<String> intersection(Set<String> left, Set<String> right) {
        Set<String> result = new HashSet<>(left);
        result.retainAll(right);
        return result;
    }

    private Set<String> union(Set<String> left, Set<String> right) {
        Set<String> result = new HashSet<>(left);
        result.addAll(right);
        return result;
    }
}
