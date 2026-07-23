package com.odin.profileservice.repo;

import com.odin.profileservice.entity.AccountDeletionOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.util.List;

public interface AccountDeletionOutboxRepository
        extends JpaRepository<AccountDeletionOutbox, String> {

    @Query("select o.eventId from AccountDeletionOutbox o "
            + "where o.status = :status "
            + "and o.nextAttemptAt <= :now "
            + "and (o.claimUntil is null or o.claimUntil < :now) "
            + "order by o.nextAttemptAt, o.createdAt")
    List<String> findPublishableEventIds(
            @Param("status") AccountDeletionOutbox.Status status,
            @Param("now") Timestamp now,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AccountDeletionOutbox o set o.claimToken = :claimToken, "
            + "o.claimUntil = :claimUntil, o.attemptCount = o.attemptCount + 1 "
            + "where o.eventId = :eventId "
            + "and o.status = :status "
            + "and o.nextAttemptAt <= :now "
            + "and (o.claimUntil is null or o.claimUntil < :now)")
    int claim(@Param("eventId") String eventId,
              @Param("status") AccountDeletionOutbox.Status status,
              @Param("claimToken") String claimToken,
              @Param("now") Timestamp now,
              @Param("claimUntil") Timestamp claimUntil);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AccountDeletionOutbox o set "
            + "o.status = :publishedStatus, "
            + "o.publishedAt = :publishedAt, o.claimToken = null, o.claimUntil = null, "
            + "o.lastErrorCategory = null where o.eventId = :eventId "
            + "and o.status = :pendingStatus "
            + "and o.claimToken = :claimToken")
    int markPublished(@Param("eventId") String eventId,
                      @Param("pendingStatus") AccountDeletionOutbox.Status pendingStatus,
                      @Param("publishedStatus") AccountDeletionOutbox.Status publishedStatus,
                      @Param("claimToken") String claimToken,
                      @Param("publishedAt") Timestamp publishedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AccountDeletionOutbox o set o.nextAttemptAt = :nextAttemptAt, "
            + "o.lastErrorCategory = :errorCategory, o.claimToken = null, o.claimUntil = null "
            + "where o.eventId = :eventId "
            + "and o.status = :status "
            + "and o.claimToken = :claimToken")
    int releaseForRetry(@Param("eventId") String eventId,
                        @Param("status") AccountDeletionOutbox.Status status,
                        @Param("claimToken") String claimToken,
                        @Param("nextAttemptAt") Timestamp nextAttemptAt,
                        @Param("errorCategory") String errorCategory);
}
