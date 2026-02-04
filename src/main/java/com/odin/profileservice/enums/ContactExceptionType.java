package com.odin.profileservice.enums;

/**
 * Contact exception types for privacy overrides.
 */
public enum ContactExceptionType {
    ALWAYS_SHOW("ALWAYS_SHOW"),
    ALWAYS_HIDE("ALWAYS_HIDE"),
    CUSTOM("CUSTOM");

    private final String value;

    ContactExceptionType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
