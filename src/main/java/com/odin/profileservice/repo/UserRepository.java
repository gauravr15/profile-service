package com.odin.profileservice.repo;

import com.odin.profileservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for User entity - handles database operations for users.
 * Supports both identity (phone_hash) and global contact (global_phone_hash) lookups.
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    /**
     * Find user by identity phone hash (per-user salted).
     * 
     * @param phoneHash SHA-256 hashed phone number with per-user salt
     * @return User if found
     */
    Optional<User> findByPhoneHash(String phoneHash);

    /**
     * Check if identity phone hash already exists.
     */
    boolean existsByPhoneHash(String phoneHash);

    /**
     * Find user by global phone hash (deterministic, cross-user).
     * Used for reverse lookups during contact matching.
     * 
     * @param globalPhoneHash SHA-256 hashed phone number with global pepper
     * @return User if found
     */
    Optional<User> findByGlobalPhoneHash(String globalPhoneHash);

    /**
     * Check if global phone hash exists.
     */
    boolean existsByGlobalPhoneHash(String globalPhoneHash);
}
