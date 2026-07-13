package com.odin.profileservice.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.odin.profileservice.entity.BlockedContact;
import com.odin.profileservice.entity.Contact;
import com.odin.profileservice.entity.ContactException;
import com.odin.profileservice.entity.User;
import com.odin.profileservice.enums.ContactExceptionType;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:status-eligibility;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
class StatusEligibilityRepositoryTest {

    @Autowired private UserRepository users;
    @Autowired private ContactRepository contacts;
    @Autowired private BlockedContactRepository blocks;
    @Autowired private ContactExceptionRepository exceptions;

    @Test
    void bulkQueriesPreserveForwardDirectionAndBothBlockDirections() {
        users.saveAll(Arrays.asList(user("1", "viewer-hash"), user("2", "uploader-hash"),
                user("3", "other-hash")));
        contacts.save(Contact.builder().ownerUserId("2").targetGlobalPhoneHash("viewer-hash").build());
        contacts.save(Contact.builder().ownerUserId("1").targetGlobalPhoneHash("other-hash").build());
        blocks.save(BlockedContact.builder().blockerUserId("2").blockedGlobalPhoneHash("viewer-hash").build());
        blocks.save(BlockedContact.builder().blockerUserId("1").blockedGlobalPhoneHash("uploader-hash").build());
        exceptions.save(ContactException.builder().ownerUserId("2").exceptionGlobalPhoneHash("viewer-hash")
                .exceptionType(ContactExceptionType.ALWAYS_HIDE).build());

        assertEquals(Collections.singletonList("2"), contacts
                .findByOwnerUserIdInAndTargetGlobalPhoneHash(Arrays.asList("2", "3"), "viewer-hash")
                .stream().map(Contact::getOwnerUserId).collect(java.util.stream.Collectors.toList()));
        assertEquals(1, blocks.findByBlockerUserIdInAndBlockedGlobalPhoneHash(
                Arrays.asList("2", "3"), "viewer-hash").size());
        assertEquals(1, blocks.findByBlockerUserIdAndBlockedGlobalPhoneHashIn(
                "1", Arrays.asList("uploader-hash", "other-hash")).size());
        assertEquals(1, exceptions.findByOwnerUserIdInAndExceptionGlobalPhoneHash(
                Arrays.asList("2", "3"), "viewer-hash").size());
    }

    private User user(String id, String globalHash) {
        return User.builder().userId(id).phoneHash("phone-" + id).phoneSalt("salt-" + id)
                .globalPhoneHash(globalHash).pepperVersion(1).build();
    }
}
