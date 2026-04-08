package com.odin.profileservice.service;

import com.odin.profileservice.dto.CreateGroupRequest;
import com.odin.profileservice.dto.GroupCreatedEvent;
import com.odin.profileservice.entity.Group;
import com.odin.profileservice.repo.GroupRepository;
import com.odin.profileservice.utility.GroupEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupEventProducer groupEventProducer;

    @Transactional
    public Group createGroup(String creatorId, CreateGroupRequest request) {
        validateCreator(creatorId);
        validateRequest(request);

        // Validate and dedupe requested members (excluding creator for min-size calc)
        List<String> sanitizedMembers = sanitize(request.getMemberIds());
        if (new LinkedHashSet<>(sanitizedMembers).size() != sanitizedMembers.size()) {
            throw new IllegalArgumentException("Duplicate member IDs are not allowed");
        }
        Set<String> requestedMembers = normalizeMembers(sanitizedMembers);

        // Build final member set (creator + requested members)
        LinkedHashSet<String> members = new LinkedHashSet<>();
        members.add(creatorId);
        members.addAll(requestedMembers);

        if (members.size() < 2) {
            throw new IllegalArgumentException("Group must have at least 2 unique members including creator");
        }

        Group group = Group.builder()
                .groupId(UUID.randomUUID().toString())
                .name(request.getName().trim())
            .members(new LinkedHashSet<>(members))
            .admins(new LinkedHashSet<>(Collections.singletonList(creatorId)))
                .createdBy(creatorId)
                .createdAt(new Timestamp(System.currentTimeMillis()))
                .avatarUrl(request.getAvatarUrl())
                .build();

        Group saved = groupRepository.save(group);
        log.info("[GROUP-CREATE] persisted groupId={} members={} admins={}",
                saved.getGroupId(),
                saved.getMembers() != null ? saved.getMembers().size() : 0,
                saved.getAdmins() != null ? saved.getAdmins().size() : 0);

        // Publish Kafka event so web-socket-service can notify members in real time
        try {
            GroupCreatedEvent event = GroupCreatedEvent.builder()
                    .groupId(saved.getGroupId())
                    .groupName(saved.getName())
                    .memberIds(new ArrayList<>(saved.getMembers()))
                    .creatorId(creatorId)
                    .createdAt(saved.getCreatedAt().getTime())
                    .build();
            groupEventProducer.publishGroupCreated(event);
        } catch (Exception e) {
            log.error("[GROUP-CREATE] Failed to publish GroupCreatedEvent for groupId={}: {}",
                    saved.getGroupId(), e.getMessage(), e);
        }

        return saved;
    }

    public List<Group> getGroupsForUser(String customerId) {
        validateCreator(customerId);
        List<Group> groups = groupRepository.findByMemberId(customerId);
        int count = groups != null ? groups.size() : 0;
        if (count == 0) {
            log.warn("[GROUP-READ] No groups found for user={} (DB empty or schema missing)", customerId);
        } else {
            log.info("[GROUP-READ] groups for user={} count={}", customerId, count);
        }
        return groups;
    }

    public Group getGroupForMember(String groupId, String memberId) {
        validateGroupId(groupId);
        validateCreator(memberId);
        Optional<Group> maybeGroup = groupRepository.findByIdWithMembers(groupId);
        if (!maybeGroup.isPresent()) {
            throw new GroupNotFoundException("Group not found");
        }

        Group group = maybeGroup.get();
        if (group.getMembers() == null || !group.getMembers().contains(memberId)) {
            throw new GroupAccessDeniedException("User not part of group");
        }
        return group;
    }

    public boolean isMember(String groupId, String memberId) {
        validateGroupId(groupId);
        validateCreator(memberId);
        return groupRepository.existsByGroupIdAndMemberId(groupId, memberId);
    }

    public Group getGroup(String groupId) {
        validateGroupId(groupId);
        Optional<Group> maybeGroup = groupRepository.findById(groupId);
        if (!maybeGroup.isPresent()) {
            throw new GroupNotFoundException("Group not found");
        }
        return maybeGroup.get();
    }

    private void validateCreator(String creatorId) {
        if (!StringUtils.hasText(creatorId)) {
            throw new IllegalArgumentException("customerId is required");
        }
    }

    private void validateGroupId(String groupId) {
        if (!StringUtils.hasText(groupId)) {
            throw new IllegalArgumentException("groupId is required");
        }
    }

    private void validateRequest(CreateGroupRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new IllegalArgumentException("Group name is required");
        }
        if (CollectionUtils.isEmpty(request.getMemberIds()) || request.getMemberIds().size() < 1) {
            throw new IllegalArgumentException("At least one additional member is required");
        }
    }

    private Set<String> normalizeMembers(List<String> memberIds) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (memberIds == null) {
            return normalized;
        }

        for (String member : memberIds) {
            if (!StringUtils.hasText(member)) {
                throw new IllegalArgumentException("Member IDs cannot be blank");
            }
            String trimmed = member.trim();
            normalized.add(trimmed);
        }
        return normalized;
    }

    private List<String> sanitize(List<String> memberIds) {
        List<String> sanitized = new ArrayList<>();
        if (memberIds == null) {
            return sanitized;
        }
        for (String member : memberIds) {
            sanitized.add(member == null ? null : member.trim());
        }
        return sanitized;
    }

    public static class GroupAccessDeniedException extends RuntimeException {
        public GroupAccessDeniedException(String message) {
            super(message);
        }
    }

    public static class GroupNotFoundException extends RuntimeException {
        public GroupNotFoundException(String message) {
            super(message);
        }
    }
}
