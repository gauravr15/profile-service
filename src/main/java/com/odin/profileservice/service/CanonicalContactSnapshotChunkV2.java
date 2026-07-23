package com.odin.profileservice.service;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Set;

@Value
@Builder
class CanonicalContactSnapshotChunkV2 {
    int submittedCount;
    List<String> canonicalPhones;
    List<String> targetTokens;
    List<String> targetCurrentTokens;
    Set<String> registeredTokens;
    String payloadDigest;
}
