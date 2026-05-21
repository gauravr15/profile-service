package com.odin.profileservice.utility;

import org.springframework.stereotype.Component;

import com.odin.profileservice.entity.Profile;

/**
 * Centralized account-state validator for all authentication entry points.
 *
 * Single source of truth for "is this user eligible to authenticate?".
 * Every auth path (OTP generation, OTP sign-in, password sign-in) MUST
 * call {@link #isEligibleForAuth(Profile)} before issuing any credential.
 *
 * A user is eligible if and only if:
 *   1. Profile exists (non-null)
 *   2. isDeleted is not true
 *   3. isActive is not false
 *
 * Using Boolean.TRUE.equals / Boolean.FALSE.equals handles null safely and
 * avoids NPE when the core service omits the field.
 */
@Component
public class AccountStateValidator {

    /**
     * Returns true when the account is present, not deleted, and active.
     * All three conditions must hold for authentication to proceed.
     */
    public boolean isEligibleForAuth(Profile profile) {
        if (profile == null) {
            return false;
        }
        if (Boolean.TRUE.equals(profile.getIsDeleted())) {
            return false;
        }
        if (Boolean.FALSE.equals(profile.getIsActive())) {
            return false;
        }
        return true;
    }
}
