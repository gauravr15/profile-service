package com.odin.profileservice.repo;

import com.odin.profileservice.entity.ContactSyncStateV2;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface ContactSyncStateV2Repository extends JpaRepository<ContactSyncStateV2, String> {

    long deleteByOwnerUserId(String ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from ContactSyncStateV2 state where state.ownerUserId = :ownerUserId")
    Optional<ContactSyncStateV2> findForUpdate(@Param("ownerUserId") String ownerUserId);
}
