package com.odin.profileservice.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ContactSnapshotRequestV2Id implements Serializable {
    private String ownerUserId;
    private String snapshotId;
}
