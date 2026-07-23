package com.odin.profileservice.repo;

import com.odin.profileservice.entity.ContactSnapshotOwnerLockV2;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;

@Repository
public interface ContactSnapshotOwnerLockV2Repository
        extends JpaRepository<ContactSnapshotOwnerLockV2, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lockRow from ContactSnapshotOwnerLockV2 lockRow "
            + "where lockRow.ownerUserId = :ownerUserId")
    ContactSnapshotOwnerLockV2 lockOwner(@Param("ownerUserId") String ownerUserId);
}
