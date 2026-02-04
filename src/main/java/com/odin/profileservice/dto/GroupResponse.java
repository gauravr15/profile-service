package com.odin.profileservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupResponse {
    private String groupId;
    private String name;
    private List<String> members;
    private List<String> admins;
    private String createdBy;
    private String createdAt;
    private String avatarUrl;
}
