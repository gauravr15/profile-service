package com.odin.profileservice.config;

import lombok.Getter;
import lombok.ToString;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@ToString(exclude = "versionSecrets")
@Component
@ConfigurationProperties(prefix = "app.contact.tokens")
public class ContactTokenProperties {
    private int currentVersion = 1;
    private List<Integer> acceptedVersions = new ArrayList<Integer>();
    private Map<String, String> versionSecrets;
    private int minimumSecretLength = 32;

    @PostConstruct
    void validate() {
        if (currentVersion < 2) {
            throw new IllegalStateException("Current contact-token version must be at least 2");
        }
        if (acceptedVersions == null || acceptedVersions.isEmpty()) {
            acceptedVersions = new ArrayList<Integer>();
            acceptedVersions.add(1);
        }

        LinkedHashSet<Integer> uniqueVersions = new LinkedHashSet<>(acceptedVersions);
        if (uniqueVersions.size() != acceptedVersions.size()) {
            throw new IllegalStateException("Duplicate accepted contact-token versions are not allowed");
        }
        if (!uniqueVersions.contains(currentVersion)) {
            throw new IllegalStateException("Current contact-token version must be accepted");
        }

        for (Integer version : uniqueVersions) {
            if (version == null || version < 1) {
                throw new IllegalStateException("Contact-token versions must be positive");
            }
        }

        if (currentVersion > 1) {
            requireSecret(currentVersion);
        }
    }

    public String requireSecret(int version) {
        String secret = versionSecrets == null ? null : versionSecrets.get(String.valueOf(version));
        if (secret == null || secret.trim().length() < minimumSecretLength) {
            throw new IllegalStateException("Missing or weak contact-token secret for version " + version);
        }
        return secret.trim();
    }
}