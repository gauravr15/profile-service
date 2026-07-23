package com.odin.profileservice.repo;

import com.odin.profileservice.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;

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
    @Query(value = "SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END FROM ("
            + "SELECT owner_user_id, target_global_phone_hash, target_global_phone_token, target_global_phone_token_version FROM contacts "
            + "WHERE owner_user_id = :ownerUserId AND target_global_phone_hash = :targetHash "
            + "AND NOT EXISTS (SELECT 1 FROM contact_sync_state_v2 s WHERE s.owner_user_id = :ownerUserId) "
            + "UNION ALL "
            + "SELECT owner_user_id, target_global_phone_hash, target_global_phone_token, target_global_phone_token_version FROM contacts_v2 "
            + "WHERE owner_user_id = :ownerUserId AND target_global_phone_hash = :targetHash "
            + "AND EXISTS (SELECT 1 FROM contact_sync_state_v2 s WHERE s.owner_user_id = :ownerUserId)"
            + ") effective_contacts", nativeQuery = true)
    boolean existsByOwnerUserIdAndTargetGlobalPhoneHash(
            @Param("ownerUserId") String ownerUserId,
            @Param("targetHash") String targetGlobalPhoneHash);

    /**
     * Get all contacts saved by a user.
     * 
     * @param ownerUserId the user who saved the contacts
     * @return list of saved contacts
     */
    @Query(value = "SELECT c.contact_id, c.owner_user_id, c.target_global_phone_hash, "
            + "c.target_global_phone_token, c.target_global_phone_token_version, c.saved_at, c.updated_at FROM contacts c WHERE c.owner_user_id = :ownerUserId "
            + "AND NOT EXISTS (SELECT 1 FROM contact_sync_state_v2 s WHERE s.owner_user_id = :ownerUserId) "
            + "UNION ALL SELECT v.contact_id, v.owner_user_id, v.target_global_phone_hash, "
            + "v.target_global_phone_token, v.target_global_phone_token_version, v.saved_at, v.updated_at FROM contacts_v2 v WHERE v.owner_user_id = :ownerUserId "
            + "AND EXISTS (SELECT 1 FROM contact_sync_state_v2 s WHERE s.owner_user_id = :ownerUserId)",
            nativeQuery = true)
    List<Contact> findByOwnerUserId(@Param("ownerUserId") String ownerUserId);

    /**
     * Count contacts saved by a user.
     */
    @Query(value = "SELECT COUNT(*) FROM ("
            + "SELECT contact_id FROM contacts WHERE owner_user_id = :ownerUserId "
            + "AND NOT EXISTS (SELECT 1 FROM contact_sync_state_v2 s WHERE s.owner_user_id = :ownerUserId) "
            + "UNION ALL SELECT contact_id FROM contacts_v2 WHERE owner_user_id = :ownerUserId "
            + "AND EXISTS (SELECT 1 FROM contact_sync_state_v2 s WHERE s.owner_user_id = :ownerUserId)"
            + ") effective_contacts", nativeQuery = true)
    long countByOwnerUserId(@Param("ownerUserId") String ownerUserId);

    /**
     * Find all contacts that saved a given global phone hash.
     * Useful for reverse lookups (who has saved this person).
     * 
     * @param targetGlobalPhoneHash the deterministic global hash to find savers for
     * @return list of contacts with this as target
     */
    @Query(value = "SELECT c.contact_id, c.owner_user_id, c.target_global_phone_hash, "
            + "c.saved_at, c.updated_at FROM contacts c WHERE c.target_global_phone_hash = :targetHash "
            + "AND NOT EXISTS (SELECT 1 FROM contact_sync_state_v2 s WHERE s.owner_user_id = c.owner_user_id) "
            + "UNION ALL SELECT v.contact_id, v.owner_user_id, v.target_global_phone_hash, "
            + "v.saved_at, v.updated_at FROM contacts_v2 v WHERE v.target_global_phone_hash = :targetHash "
            + "AND EXISTS (SELECT 1 FROM contact_sync_state_v2 s WHERE s.owner_user_id = v.owner_user_id)",
            nativeQuery = true)
    List<Contact> findByTargetGlobalPhoneHash(@Param("targetHash") String targetGlobalPhoneHash);

    /**
     * Delete a specific contact relationship.
     */
    long deleteByOwnerUserIdAndTargetGlobalPhoneHash(String ownerUserId, String targetGlobalPhoneHash);

    long deleteAllByOwnerUserId(String ownerUserId);

    long deleteAllByTargetGlobalPhoneHash(String targetGlobalPhoneHash);

    @Query(value = "SELECT c.contact_id, c.owner_user_id, c.target_global_phone_hash, "
            + "c.target_global_phone_token, c.target_global_phone_token_version, c.saved_at, c.updated_at FROM contacts c WHERE c.owner_user_id IN (:ownerUserIds) "
            + "AND c.target_global_phone_hash = :targetHash "
            + "AND NOT EXISTS (SELECT 1 FROM contact_sync_state_v2 s WHERE s.owner_user_id = c.owner_user_id) "
            + "UNION ALL SELECT v.contact_id, v.owner_user_id, v.target_global_phone_hash, "
            + "v.target_global_phone_token, v.target_global_phone_token_version, v.saved_at, v.updated_at FROM contacts_v2 v WHERE v.owner_user_id IN (:ownerUserIds) "
            + "AND v.target_global_phone_hash = :targetHash "
            + "AND EXISTS (SELECT 1 FROM contact_sync_state_v2 s WHERE s.owner_user_id = v.owner_user_id)",
            nativeQuery = true)
    List<Contact> findByOwnerUserIdInAndTargetGlobalPhoneHash(
            @Param("ownerUserIds") Collection<String> ownerUserIds,
            @Param("targetHash") String targetGlobalPhoneHash);
}
