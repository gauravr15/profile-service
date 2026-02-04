package com.odin.profileservice.repo;

import com.odin.profileservice.entity.SyncAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for SyncAudit entity.
 */
@Repository
public interface SyncAuditRepository extends JpaRepository<SyncAudit, String> {
}
