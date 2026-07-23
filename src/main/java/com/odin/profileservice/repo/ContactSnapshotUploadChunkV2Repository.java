package com.odin.profileservice.repo;

import com.odin.profileservice.entity.ContactSnapshotUploadChunkV2;
import com.odin.profileservice.entity.ContactSnapshotUploadChunkV2Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContactSnapshotUploadChunkV2Repository
        extends JpaRepository<ContactSnapshotUploadChunkV2, ContactSnapshotUploadChunkV2Id> {

    List<ContactSnapshotUploadChunkV2> findBySessionIdOrderByChunkIndex(String sessionId);

    void deleteBySessionId(String sessionId);
}
