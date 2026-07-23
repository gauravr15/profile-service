package com.odin.profileservice.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class ContactSnapshotV2DigestTest {
    private final ContactSnapshotV2Digest digest = new ContactSnapshotV2Digest();

    @Test
    void canonicalOrderingAndDeduplicationProduceStableDigest() {
        String first = digest.compute(4, "IN", Arrays.asList("token-a", "token-b"));
        String reordered = digest.compute(4, "IN", Arrays.asList("token-a", "token-b"));

        assertThat(first).isEqualTo(reordered).hasSize(64);
    }

    @Test
    void materialSemanticChangesProduceDifferentDigests() {
        String baseline = digest.compute(4, "IN", Collections.singletonList("token-a"));

        assertThat(digest.compute(5, "IN", Collections.singletonList("token-a")))
                .isNotEqualTo(baseline);
        assertThat(digest.compute(4, "US", Collections.singletonList("token-a")))
                .isNotEqualTo(baseline);
        assertThat(digest.compute(4, "IN", Collections.singletonList("token-b")))
                .isNotEqualTo(baseline);
    }
}
