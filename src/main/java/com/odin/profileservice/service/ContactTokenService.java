package com.odin.profileservice.service;

import com.odin.profileservice.config.ContactTokenProperties;
import com.odin.profileservice.utility.PhoneNumberHasher;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@RequiredArgsConstructor
public class ContactTokenService {
    public enum Purpose {
        GLOBAL_LOOKUP,
        DISCOVERY_RATE_LIMIT,
        DISCOVERY_DAILY_UNIQUE,
        TEMPORARY_UPLOAD,
        OWNER_RELATIONSHIP
    }

    private final PhoneNumberHasher phoneNumberHasher;
    private final ContactTokenProperties properties;

    public int getCurrentVersion() {
        return properties.getCurrentVersion();
    }

    public String canonicalize(String phoneNumber, String region) {
        return phoneNumberHasher.normalizePhoneNumber(phoneNumber, region);
    }

    public String canonicalize(String phoneNumber) {
        return phoneNumberHasher.normalizePhoneNumber(phoneNumber);
    }

    public LookupTokenMaterial deriveLookupTokens(String phoneNumber, String region) {
        return deriveLookupTokensFromCanonical(canonicalize(phoneNumber, region));
    }

    public LookupTokenMaterial deriveLookupTokensFromCanonical(String canonicalPhone) {
        String legacyToken = phoneNumberHasher.hashWithGlobalPepper(canonicalPhone);
        String currentToken = deriveVersionedToken(Purpose.GLOBAL_LOOKUP, canonicalPhone,
                properties.getCurrentVersion());
        return new LookupTokenMaterial(canonicalPhone, legacyToken, currentToken,
                properties.getCurrentVersion());
    }

    public List<String> deriveCompatibleLookupTokensFromCanonical(String canonicalPhone) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        tokens.add(phoneNumberHasher.hashWithGlobalPepper(canonicalPhone));
        for (Integer version : properties.getAcceptedVersions()) {
            if (version != null) {
                tokens.add(deriveVersionedToken(Purpose.GLOBAL_LOOKUP, canonicalPhone, version));
            }
        }
        return new ArrayList<>(tokens);
    }

    public String deriveDiscoveryAccountToken(String customerId) {
        return deriveVersionedToken(Purpose.DISCOVERY_RATE_LIMIT, "account:" + customerId,
                properties.getCurrentVersion());
    }

    public String deriveDiscoveryPhoneToken(String canonicalPhone) {
        return deriveVersionedToken(Purpose.DISCOVERY_DAILY_UNIQUE, "phone:" + canonicalPhone,
                properties.getCurrentVersion());
    }

    public String deriveTemporaryUploadToken(String canonicalPhone) {
        return deriveVersionedToken(Purpose.TEMPORARY_UPLOAD, canonicalPhone,
                properties.getCurrentVersion());
    }

    public String deriveRelationshipToken(String ownerUserId, String canonicalPhone) {
        return deriveVersionedToken(Purpose.OWNER_RELATIONSHIP,
                ownerUserId + ":" + canonicalPhone, properties.getCurrentVersion());
    }

    public String deriveVersionedToken(Purpose purpose, String value, int version) {
        if (version < 1) {
            throw new IllegalArgumentException("Token version must be positive");
        }
        if (version == 1) {
            return phoneNumberHasher.hashWithGlobalPepper(stripPrefix(purpose, value));
        }
        byte[] keyMaterial = hmac(properties.requireSecret(version).getBytes(StandardCharsets.UTF_8),
                purpose.name() + ":" + version);
        return toHex(hmac(keyMaterial, value));
    }

    public String deriveCurrentVersionedToken(Purpose purpose, String value) {
        return deriveVersionedToken(purpose, value, properties.getCurrentVersion());
    }

    private String stripPrefix(Purpose purpose, String value) {
        String prefix = purpose.name().toLowerCase() + ":";
        if (value != null && value.startsWith(prefix)) {
            return value.substring(prefix.length());
        }
        return value;
    }

    private byte[] hmac(byte[] keyBytes, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to derive contact token", ex);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }

    @Getter
    public static final class LookupTokenMaterial {
        private final String canonicalPhone;
        private final String legacyToken;
        private final String currentToken;
        private final int currentVersion;

        public LookupTokenMaterial(String canonicalPhone, String legacyToken,
                String currentToken, int currentVersion) {
            this.canonicalPhone = canonicalPhone;
            this.legacyToken = legacyToken;
            this.currentToken = currentToken;
            this.currentVersion = currentVersion;
        }

        public List<String> allTokens() {
            LinkedHashSet<String> tokens = new LinkedHashSet<>();
            tokens.add(legacyToken);
            tokens.add(currentToken);
            return new ArrayList<>(tokens);
        }
    }
}