package com.odin.profileservice.repo;

import com.odin.profileservice.entity.ContactException;
import com.odin.profileservice.enums.ContactExceptionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

/**
 * Repository for ContactException entity - handles privacy exceptions for specific contacts.
 * All queries use global phone hashes (deterministic, cross-user).
 */
@Repository
public interface ContactExceptionRepository extends JpaRepository<ContactException, String> {

    /**
     * Find an exception for a user and global phone hash.
     * 
     * @param ownerUserId the user who owns the exception
     * @param exceptionGlobalPhoneHash the deterministic global hash with an exception
     * @return ContactException if found
     */
    Optional<ContactException> findByOwnerUserIdAndExceptionGlobalPhoneHash(String ownerUserId, String exceptionGlobalPhoneHash);

    /**
     * Get all exceptions for a user.
     * 
     * @param ownerUserId the user who owns the exceptions
     * @return list of contact exceptions
     */
    List<ContactException> findByOwnerUserId(String ownerUserId);

    /**
     * Delete an exception.
     */
    long deleteByOwnerUserIdAndExceptionGlobalPhoneHash(String ownerUserId, String exceptionGlobalPhoneHash);

    List<ContactException> findByOwnerUserIdInAndExceptionGlobalPhoneHash(
            Collection<String> ownerUserIds, String exceptionGlobalPhoneHash);
}
