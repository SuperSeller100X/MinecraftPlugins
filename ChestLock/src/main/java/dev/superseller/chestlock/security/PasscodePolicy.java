package dev.superseller.chestlock.security;

/** Passcode input validation shared by lock and passcode-change workflows. */
public final class PasscodePolicy {
    private PasscodePolicy() {
    }

    public static boolean isValid(String value, int minimumLength, int maximumLength) {
        if (value == null || value.length() < minimumLength || value.length() > maximumLength) {
            return false;
        }
        if (!value.equals(value.strip())) {
            return false;
        }
        return value.codePoints().noneMatch(Character::isISOControl);
    }
}
