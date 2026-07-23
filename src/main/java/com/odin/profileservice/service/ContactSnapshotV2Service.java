package com.odin.profileservice.service;

import com.odin.profileservice.dto.ContactSnapshotV2Request;
import com.odin.profileservice.dto.ContactSnapshotV2Response;
import com.odin.profileservice.entity.ContactSnapshotRequestV2;
import com.odin.profileservice.entity.ContactSnapshotRequestV2Id;
import com.odin.profileservice.entity.Profile;
import com.odin.profileservice.repo.ContactSnapshotOwnerLockV2Repository;
import com.odin.profileservice.repo.ContactSnapshotRequestV2Repository;
import com.odin.profileservice.repo.ProfileRepository;
import com.odin.profileservice.utility.PhoneNumberHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactSnapshotV2Service {
    private static final int MAX_CONTACTS = 5000;
    private static final int MAX_PHONE_LENGTH = 32;
    private static final Pattern COUNTRY_CODE = Pattern.compile("^[A-Z]{2}$");

    private final ContactSnapshotRequestV2Repository requestRepository;
    private final ContactSnapshotOwnerLockV2Repository ownerLockRepository;
    private final ContactSnapshotOwnerLockV2Initializer ownerLockInitializer;
    private final ContactSnapshotV2CommitService commitService;
    private final ContactSnapshotV2Digest digest;
    private final ProfileRepository profileRepository;
    private final PhoneNumberHasher phoneNumberHasher;
    private final ContactTokenService contactTokenService;

    public ContactSnapshotV2Response replaceSnapshot(
            String ownerCustomerId,
            ContactSnapshotV2Request request) {
        CanonicalContactSnapshotV2 canonical = validateAndCanonicalize(request);
        Profile ownerProfile = validateOwner(ownerCustomerId);
        int registeredCount = resolveRegisteredCount(canonical.getCanonicalPhones());
        canonical = canonical.toBuilder()
                .ownerProfile(ownerProfile)
                .registeredCount(registeredCount)
                .build();

        ensureOwnerLock(ownerCustomerId);
        return commitService.commit(ownerCustomerId, canonical);
    }

    private CanonicalContactSnapshotV2 validateAndCanonicalize(
            ContactSnapshotV2Request request) {
        if (request == null) {
            throw new ContactSnapshotV2ValidationException("Snapshot request is required");
        }
        String snapshotId = validateSnapshotId(request.getSnapshotId());
        if (request.getBaseRevision() == null || request.getBaseRevision() < 0) {
            throw new ContactSnapshotV2ValidationException("base_revision must be nonnegative");
        }
        String countryCode = validateCountryCode(request.getCountryCode());
        if (request.getContacts() == null) {
            throw new ContactSnapshotV2ValidationException("contacts must not be null");
        }
        if (request.getContacts().size() > MAX_CONTACTS) {
            throw new ContactSnapshotV2ValidationException("contacts exceeds the V2 limit");
        }

        CanonicalContactSnapshotChunkV2 chunk =
                canonicalizeContacts(countryCode, request.getContacts(), MAX_CONTACTS);
        String payloadDigest = digest.compute(
                request.getBaseRevision(), countryCode, chunk.getTargetTokens());
        return CanonicalContactSnapshotV2.builder()
                .snapshotId(snapshotId)
                .baseRevision(request.getBaseRevision())
                .countryCode(countryCode)
                .submittedCount(chunk.getSubmittedCount())
                .canonicalPhones(chunk.getCanonicalPhones())
                .targetTokens(chunk.getTargetTokens())
            .targetCurrentTokens(chunk.getTargetCurrentTokens())
                .payloadDigest(payloadDigest)
                .build();
    }

    CanonicalContactSnapshotChunkV2 canonicalizeChunk(
            String countryCode,
            List<ContactSnapshotV2Request.ContactItem> contacts,
            int maximumContacts) {
        String canonicalCountry = validateCountryCode(countryCode);
        CanonicalContactSnapshotChunkV2 canonical =
                canonicalizeContacts(canonicalCountry, contacts, maximumContacts);
        Set<String> registeredTokens = resolveRegisteredTokens(canonical.getCanonicalPhones());
        return CanonicalContactSnapshotChunkV2.builder()
                .submittedCount(canonical.getSubmittedCount())
                .canonicalPhones(canonical.getCanonicalPhones())
                .targetTokens(canonical.getTargetTokens())
            .targetCurrentTokens(canonical.getTargetCurrentTokens())
                .registeredTokens(registeredTokens)
                .payloadDigest(digest.compute(0, canonicalCountry, canonical.getTargetTokens()))
                .build();
    }

    CanonicalContactSnapshotV2 prepareUploadedSnapshot(
            String snapshotId,
            long baseRevision,
            String countryCode,
            int submittedCount,
            List<String> sortedTargetTokens,
            List<String> sortedCurrentTokens,
            int registeredCount,
            Profile ownerProfile) {
        String canonicalCountry = validateCountryCode(countryCode);
        return CanonicalContactSnapshotV2.builder()
                .snapshotId(validateSnapshotId(snapshotId))
                .baseRevision(baseRevision)
                .countryCode(canonicalCountry)
                .submittedCount(submittedCount)
                .canonicalPhones(Collections.emptyList())
                .targetTokens(sortedTargetTokens)
                .targetCurrentTokens(sortedCurrentTokens)
                .registeredCount(registeredCount)
                .ownerProfile(ownerProfile)
                .payloadDigest(digest.compute(baseRevision, canonicalCountry, sortedTargetTokens))
                .build();
    }

    private CanonicalContactSnapshotChunkV2 canonicalizeContacts(
            String countryCode,
            List<ContactSnapshotV2Request.ContactItem> contacts,
            int maximumContacts) {
        if (contacts == null) {
            throw new ContactSnapshotV2ValidationException("contacts must not be null");
        }
        if (contacts.size() > maximumContacts) {
            throw new ContactSnapshotV2ValidationException("contacts exceeds the V2 limit");
        }
        Set<String> canonicalPhones = new HashSet<>();
        for (ContactSnapshotV2Request.ContactItem item : contacts) {
            if (item == null) {
                throw new ContactSnapshotV2ValidationException(
                        "contacts must not contain null items");
            }
            String phone = item.getPhoneNumber();
            if (phone == null || phone.trim().isEmpty() || phone.length() > MAX_PHONE_LENGTH) {
                throw new ContactSnapshotV2ValidationException("contact phone is invalid");
            }
            try {
                canonicalPhones.add(phoneNumberHasher.normalizePhoneNumber(phone, countryCode));
            } catch (IllegalArgumentException ex) {
                throw new ContactSnapshotV2ValidationException(
                        "snapshot contains a malformed phone number");
            }
        }
        List<String> sortedPhones = new ArrayList<>(canonicalPhones);
        Collections.sort(sortedPhones);
        List<ContactTokenService.LookupTokenMaterial> tokenMaterials = sortedPhones.stream()
            .map(contactTokenService::deriveLookupTokensFromCanonical)
            .collect(Collectors.toList());
        List<String> sortedTargetTokens = tokenMaterials.stream()
            .map(ContactTokenService.LookupTokenMaterial::getLegacyToken)
            .collect(Collectors.toList());
        List<String> sortedCurrentTokens = tokenMaterials.stream()
            .map(ContactTokenService.LookupTokenMaterial::getCurrentToken)
                .collect(Collectors.toList());
        return CanonicalContactSnapshotChunkV2.builder()
                .submittedCount(contacts.size())
                .canonicalPhones(sortedPhones)
                .targetTokens(sortedTargetTokens)
            .targetCurrentTokens(sortedCurrentTokens)
                .build();
    }

    String validateSnapshotId(String value) {
        if (value == null || value.length() != 36) {
            throw new ContactSnapshotV2ValidationException("snapshot_id must be a UUID");
        }
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException();
            }
            return parsed.toString();
        } catch (IllegalArgumentException ex) {
            throw new ContactSnapshotV2ValidationException("snapshot_id must be a UUID");
        }
    }

    String validateCountryCode(String value) {
        if (value == null
                || !COUNTRY_CODE.matcher(value.toUpperCase(Locale.ROOT)).matches()) {
            throw new ContactSnapshotV2ValidationException(
                    "country_code must be an ISO alpha-2 code");
        }
        return value.toUpperCase(Locale.ROOT);
    }

    Profile validateOwner(String ownerCustomerId) {
        final int numericId;
        try {
            numericId = Integer.parseInt(ownerCustomerId);
        } catch (NumberFormatException ex) {
            throw new ContactSnapshotV2ValidationException(
                    "Authenticated customer identity is invalid");
        }
        Profile profile = profileRepository.findByCustomerId(numericId);
        if (profile == null || profile.getMobile() == null) {
            throw new ContactSnapshotV2ValidationException(
                    "Authenticated customer does not exist");
        }
        return profile;
    }

    private int resolveRegisteredCount(List<String> canonicalPhones) {
        return resolveRegisteredTokens(canonicalPhones).size();
    }

    private Set<String> resolveRegisteredTokens(List<String> canonicalPhones) {
        if (canonicalPhones.isEmpty()) {
            return Collections.emptySet();
        }
        List<Profile> profiles = profileRepository.findLikeMobileNumber(canonicalPhones, true);
        if (profiles == null) {
            throw new IllegalStateException("Registered-contact dependency returned no result");
        }
        Set<String> registeredPhones = profiles.stream()
                .map(Profile::getMobile)
                .filter(value -> value != null)
                .map(value -> value.replaceAll("[^0-9]", ""))
                .collect(Collectors.toSet());
        return canonicalPhones.stream()
                .filter(registeredPhones::contains)
                .map(phoneNumberHasher::hashWithGlobalPepper)
                .collect(Collectors.toSet());
    }

    void ensureOwnerLock(String ownerCustomerId) {
        if (ownerLockRepository.existsById(ownerCustomerId)) {
            return;
        }
        try {
            ownerLockInitializer.create(ownerCustomerId);
        } catch (DataIntegrityViolationException ex) {
            log.debug("V2 snapshot owner lock concurrently established");
        }
    }
}
