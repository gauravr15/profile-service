package com.odin.profileservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContactTokenPropertiesTest {

    @Test
    void currentVersionRequiresConfiguredSecretAndDoesNotLeakSecretsInToString() {
        ContactTokenProperties properties = new ContactTokenProperties();
        properties.setCurrentVersion(2);
        properties.setAcceptedVersions(List.of(1, 2));
        properties.setVersionSecrets(Map.of("2", "test-only-contact-token-version-2-change-me-32chars!!"));

        ReflectionTestUtils.invokeMethod(properties, "validate");

        assertThat(properties.toString()).doesNotContain("test-only-contact-token-version-2-change-me-32chars!!");
    }

    @Test
    void missingCurrentVersionSecretFailsClosed() {
        ContactTokenProperties properties = new ContactTokenProperties();
        properties.setCurrentVersion(2);
        properties.setAcceptedVersions(List.of(1, 2));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(properties, "validate"))
                .hasMessageContaining("Missing or weak contact-token secret for version 2");
    }

    @Test
    void versionOneCannotBeSelectedAsCurrentWritableVersion() {
        ContactTokenProperties properties = new ContactTokenProperties();
        properties.setCurrentVersion(1);
        properties.setAcceptedVersions(List.of(1));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(properties, "validate"))
                .hasMessageContaining("Current contact-token version must be at least 2");
    }
}