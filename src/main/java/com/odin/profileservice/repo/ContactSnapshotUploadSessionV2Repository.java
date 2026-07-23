package com.odin.profileservice.repo;

import com.odin.profileservice.entity.ContactSnapshotUploadSessionV2;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContactSnapshotUploadSessionV2Repository
        extends JpaRepository<ContactSnapshotUploadSessionV2, String> {

    Optional<ContactSnapshotUploadSessionV2> findByOwnerUserIdAndSnapshotId(
            String ownerUserId,
            String snapshotId);

    long countByOwnerUserIdAndStateAndExpiresAtAfter(
            String ownerUserId,
            ContactSnapshotUploadSessionV2.State state,
            Timestamp now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from ContactSnapshotUploadSessionV2 session "
            + "where session.sessionId = :sessionId and session.ownerUserId = :ownerUserId")
    Optional<ContactSnapshotUploadSessionV2> findOwnedForUpdate(
            @Param("ownerUserId") String ownerUserId,
            @Param("sessionId") String sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from ContactSnapshotUploadSessionV2 session "
            + "where session.sessionId = :sessionId")
    Optional<ContactSnapshotUploadSessionV2> findForUpdate(
            @Param("sessionId") String sessionId);

    @Query("select session.sessionId from ContactSnapshotUploadSessionV2 session "
            + "where session.state = :state and session.expiresAt <= :now "
            + "order by session.expiresAt")
    List<String> findExpiredSessionIds(
            @Param("state") ContactSnapshotUploadSessionV2.State state,
            @Param("now") Timestamp now,
            Pageable pageable);

    long deleteAllByOwnerUserId(String ownerUserId);
}
