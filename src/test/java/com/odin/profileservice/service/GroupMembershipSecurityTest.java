package com.odin.profileservice.service;

import com.odin.profileservice.repo.GroupRepository;
import com.odin.profileservice.utility.GroupEventProducer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroupMembershipSecurityTest {

    private static final String GROUP_ID = "3f7b7cc1-0c31-4ef0-87bb-cd6317f3280a";
    private final GroupRepository repository = mock(GroupRepository.class);
    private final GroupService service = new GroupService(repository, mock(GroupEventProducer.class));

    @Test
    void membershipUsesAuthoritativeRepositoryResult() {
        when(repository.existsByGroupIdAndMemberId(GROUP_ID, "70")).thenReturn(false);

        assertFalse(service.isMember(GROUP_ID, "70"));
        verify(repository).existsByGroupIdAndMemberId(GROUP_ID, "70");
    }

    @Test
    void malformedGroupIdIsRejectedBeforeRepositoryLookup() {
        assertThrows(IllegalArgumentException.class, () -> service.isMember("not-a-uuid", "70"));
    }

    @Test
    void unsafeCustomerIdentifierIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.isMember(GROUP_ID, "../70"));
    }
}
