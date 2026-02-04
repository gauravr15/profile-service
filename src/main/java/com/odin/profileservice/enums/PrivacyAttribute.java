package com.odin.profileservice.enums;

/**
 * Privacy attributes that can be restricted.
 */
public enum PrivacyAttribute {
    PHOTO("photo"),
    STATUS("status"),
    LAST_SEEN("last_seen");

    private final String value;

    PrivacyAttribute(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static PrivacyAttribute fromString(String value) {
        for (PrivacyAttribute attr : PrivacyAttribute.values()) {
            if (attr.value.equalsIgnoreCase(value)) {
                return attr;
            }
        }
        throw new IllegalArgumentException("Invalid PrivacyAttribute: " + value);
    }
}
