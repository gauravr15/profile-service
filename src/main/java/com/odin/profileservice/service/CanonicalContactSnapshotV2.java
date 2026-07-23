package com.odin.profileservice.service;

import com.odin.profileservice.entity.Profile;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder(toBuilder = true)
class CanonicalContactSnapshotV2 {
    String snapshotId;
    long baseRevision;
    String countryCode;
    int submittedCount;
    List<String> canonicalPhones;
    List<String> targetTokens;
    List<String> targetCurrentTokens;
    String payloadDigest;
    int registeredCount;
    Profile ownerProfile;
}
