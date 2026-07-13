//package com.odin.profileservice.service;
//
//import com.odin.profileservice.dto.CreateGroupRequest;
//import com.odin.profileservice.entity.Group;
//import com.odin.profileservice.repo.GroupRepository;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import java.sql.Timestamp;
//import java.util.Arrays;
//import java.util.Collections;
//import java.util.Optional;
//import java.util.Set;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertThrows;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.when;
//
//@ExtendWith(MockitoExtension.class)
//class GroupServiceTest {
//
//    @Mock
//    private GroupRepository groupRepository;
//
//    @InjectMocks
//    private GroupService groupService;
//
//    @Test
//    void createGroupAddsCreatorAndAdmin() {
//        CreateGroupRequest request = CreateGroupRequest.builder()
//                .name("Trip to Goa")
//                .memberIds(Arrays.asList("u1", "u2"))
//                .build();
//
//        when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));
//
//        Group group = groupService.createGroup("creator", request);
//
//        assertEquals(3, group.getMembers().size());
//        assertEquals(Set.of("creator"), group.getAdmins());
//    }
//
//    @Test
//    void createGroupRejectsDuplicateMembers() {
//        CreateGroupRequest request = CreateGroupRequest.builder()
//                .name("Dup Test")
//                .memberIds(Arrays.asList("u1", "u1"))
//                .build();
//
//        assertThrows(IllegalArgumentException.class, () -> groupService.createGroup("creator", request));
//    }
//
//    @Test
//    void createGroupRejectsTooSmall() {
//        CreateGroupRequest request = CreateGroupRequest.builder()
//                .name("Small")
//                .memberIds(Collections.singletonList("u1"))
//                .build();
//
//        assertThrows(IllegalArgumentException.class, () -> groupService.createGroup("creator", request));
//    }
//
//    @Test
//    void getGroupForMemberThrowsWhenNotFound() {
//        when(groupRepository.findByIdWithMembers("g1")).thenReturn(Optional.empty());
//        assertThrows(GroupService.GroupNotFoundException.class, () -> groupService.getGroupForMember("g1", "u1"));
//    }
//
//    @Test
//    void getGroupForMemberThrowsWhenNotMember() {
//        Group group = Group.builder()
//                .groupId("g1")
//            .members(Set.of("a", "b"))
//            .admins(Set.of("a"))
//                .createdBy("a")
//                .createdAt(new Timestamp(System.currentTimeMillis()))
//                .name("Test")
//                .build();
//
//        when(groupRepository.findByIdWithMembers("g1")).thenReturn(Optional.of(group));
//
//        assertThrows(GroupService.GroupAccessDeniedException.class, () -> groupService.getGroupForMember("g1", "u1"));
//    }
//}
