package com.odin.profileservice.service;

import com.odin.profileservice.entity.ContactSnapshotOwnerLockV2;
import com.odin.profileservice.repo.ContactSnapshotOwnerLockV2Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;

@Service
@RequiredArgsConstructor
public class ContactSnapshotOwnerLockV2Initializer {
    private final ContactSnapshotOwnerLockV2Repository lockRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(String ownerUserId) {
        lockRepository.saveAndFlush(ContactSnapshotOwnerLockV2.builder()
                .ownerUserId(ownerUserId)
                .createdAt(new Timestamp(System.currentTimeMillis()))
                .contactLifecycleEpoch(0L)
                .build());
    }
}
