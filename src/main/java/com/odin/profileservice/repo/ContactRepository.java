package com.odin.profileservice.repo;

import com.odin.profileservice.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Contact entity - handles contact relationship persistence.
 * All queries use global phone hashes (deterministic, cross-user).
 */
@Repository
public interface ContactRepository extends JpaRepository<Contact, String> {

    /**
     * Check if owner has saved this global phone hash.
     * Used for privacy evaluation (MY_CONTACTS rule).
     * 
     * @param ownerUserId the user who saved the contact
     * @param targetGlobalPhoneHash the deterministic global hash being checked
     * @return true if contact is saved
     */
    boolean existsByOwnerUserIdAndTargetGlobalPhoneHash(String ownerUserId, String targetGlobalPhoneHash);

    /**
     * Get all contacts saved by a user.
     * 
     * @param ownerUserId the user who saved the contacts
     * @return list of saved contacts
     */
    List<Contact> findByOwnerUserId(String ownerUserId);

    /**
     * Count contacts saved by a user.
     */
    long countByOwnerUserId(String ownerUserId);

    /**
     * Find all contacts that saved a given global phone hash.
     * Useful for reverse lookups (who has saved this person).
     * 
     * @param targetGlobalPhoneHash the deterministic global hash to find savers for
     * @return list of contacts with this as target
     */
    List<Contact> findByTargetGlobalPhoneHash(String targetGlobalPhoneHash);

    /**
     * Delete a specific contact relationship.
     */
    long deleteByOwnerUserIdAndTargetGlobalPhoneHash(String ownerUserId, String targetGlobalPhoneHash);
}
