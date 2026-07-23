package com.odin.profileservice.service;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Component
public class ContactSnapshotV2Digest {
    private static final String DIGEST_VERSION = "odin-contact-snapshot-v2:1";

    public String compute(long baseRevision, String countryCode, List<String> sortedTargetTokens) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            add(digest, DIGEST_VERSION);
            add(digest, Long.toString(baseRevision));
            add(digest, countryCode);
            for (String targetToken : sortedTargetTokens) {
                add(digest, targetToken);
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Required digest algorithm unavailable", ex);
        }
    }

    private void add(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private String toHex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) {
            result.append(Character.forDigit((item >>> 4) & 0x0f, 16));
            result.append(Character.forDigit(item & 0x0f, 16));
        }
        return result.toString();
    }
}
