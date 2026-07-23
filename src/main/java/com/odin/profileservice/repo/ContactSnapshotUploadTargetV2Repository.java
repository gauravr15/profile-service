package com.odin.profileservice.repo;

import com.odin.profileservice.entity.ContactSnapshotUploadTargetV2;
import com.odin.profileservice.entity.ContactSnapshotUploadTargetV2Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ContactSnapshotUploadTargetV2Repository
        extends JpaRepository<ContactSnapshotUploadTargetV2, ContactSnapshotUploadTargetV2Id> {

    List<ContactSnapshotUploadTargetV2> findBySessionIdOrderByTargetGlobalPhoneHash(
            String sessionId);

    @Query("select target from ContactSnapshotUploadTargetV2 target "
            + "where target.sessionId = :sessionId "
            + "and target.targetGlobalPhoneHash in :tokens")
    List<ContactSnapshotUploadTargetV2> findExisting(
            @Param("sessionId") String sessionId,
            @Param("tokens") Collection<String> tokens);

    void deleteBySessionId(String sessionId);
}
