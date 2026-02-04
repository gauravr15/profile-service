package com.odin.profileservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.sql.Timestamp;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Group entity for chat metadata. Stores members/admins as element collections and
 * keeps creator/admin details for authorization.
 */
@Entity
@Table(name = "chat_groups", indexes = {
        @Index(name = "idx_chat_groups_created_by", columnList = "created_by"),
        @Index(name = "idx_chat_groups_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Group {

    @Id
    @Column(name = "group_id", columnDefinition = "VARCHAR(36)", updatable = false)
    private String groupId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "chat_group_members", joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "member_id", nullable = false, length = 36)
    private Set<String> members = new LinkedHashSet<>();

    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "chat_group_admins", joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "admin_id", nullable = false, length = 36)
    private Set<String> admins = new LinkedHashSet<>();

    @Column(name = "created_by", nullable = false, length = 36)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    @Column(name = "avatar_url", length = 2048)
    private String avatarUrl;

    @PrePersist
    protected void onCreate() {
        if (groupId == null || groupId.trim().isEmpty()) {
            groupId = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = new Timestamp(System.currentTimeMillis());
        }
    }
}
