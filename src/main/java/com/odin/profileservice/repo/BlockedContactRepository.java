package com.odin.profileservice.repo;

import com.odin.profileservice.entity.BlockedContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for BlockedContact entity - handles blocked user relationships.
 * All queries use global phone hashes (deterministic, cross-user).
 */
@Repository
public interface BlockedContactRepository extends JpaRepository<BlockedContact, String> {

    /**
     * Check if a user has blocked a global phone hash (highest priority in privacy check).
     * 
     * @param blockerUserId the user who blocked
     * @param blockedGlobalPhoneHash the deterministic global hash that is blocked
     * @return true if blocked
     */
    boolean existsByBlockerUserIdAndBlockedGlobalPhoneHash(String blockerUserId, String blockedGlobalPhoneHash);

    /**
     * Get all contacts blocked by a user.
     * 
     * @param blockerUserId the user who did the blocking
     * @return list of blocked contacts
     */
    List<BlockedContact> findByBlockerUserId(String blockerUserId);

    /**
     * Delete a block relationship.
     */
    long deleteByBlockerUserIdAndBlockedGlobalPhoneHash(String blockerUserId, String blockedGlobalPhoneHash);
}
