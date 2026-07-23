package com.odin.profileservice.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ContactSnapshotUploadTargetV2Id implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sessionId;
    private String targetGlobalPhoneHash;
}
