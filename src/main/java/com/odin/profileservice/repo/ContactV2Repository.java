package com.odin.profileservice.repo;

import com.odin.profileservice.entity.ContactV2;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContactV2Repository extends JpaRepository<ContactV2, String> {
    List<ContactV2> findByOwnerUserId(String ownerUserId);

    long deleteAllByOwnerUserId(String ownerUserId);

    long deleteAllByTargetGlobalPhoneHash(String targetGlobalPhoneHash);
}
