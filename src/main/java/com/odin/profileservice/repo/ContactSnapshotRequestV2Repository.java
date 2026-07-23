package com.odin.profileservice.repo;

import com.odin.profileservice.entity.ContactSnapshotRequestV2;
import com.odin.profileservice.entity.ContactSnapshotRequestV2Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContactSnapshotRequestV2Repository
        extends JpaRepository<ContactSnapshotRequestV2, ContactSnapshotRequestV2Id> {

    List<ContactSnapshotRequestV2> findByOwnerUserIdOrderByCreatedAtDesc(String ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from ContactSnapshotRequestV2 request "
            + "where request.ownerUserId = :ownerUserId and request.snapshotId = :snapshotId")
    Optional<ContactSnapshotRequestV2> findForUpdate(
            @Param("ownerUserId") String ownerUserId,
            @Param("snapshotId") String snapshotId);

}
