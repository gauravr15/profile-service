package com.odin.profileservice.enums;

/**
 * Privacy levels for profile attributes (photo, status, last_seen).
 * Values map to database integers: 0=EVERYONE, 1=MY_CONTACTS, 2=NOBODY
 */
public enum PrivacyLevel {
    EVERYONE(0),
    MY_CONTACTS(1),
    NOBODY(2);

    private final int value;

    PrivacyLevel(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static PrivacyLevel fromValue(int value) {
        for (PrivacyLevel level : PrivacyLevel.values()) {
            if (level.value == value) {
                return level;
            }
        }
        throw new IllegalArgumentException("Invalid PrivacyLevel value: " + value);
    }
}
