package com.odin.profileservice.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.odin.profileservice.entity.WithdrawalLock;

@Repository
public interface WithdrawalLockRepository
        extends JpaRepository<WithdrawalLock, Long> {

    Optional<WithdrawalLock>
    findFirstByStatusOrderByLockedAtDesc(
            String status);
}