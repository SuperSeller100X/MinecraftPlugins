package dev.superseller.chestlock.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasscodePolicyTest {
    @Test
    void acceptsSymbolsAndUnicodeWithinBounds() {
        assertTrue(PasscodePolicy.isValid("S3cure!", 4, 64));
        assertTrue(PasscodePolicy.isValid("Schlüssel-42", 4, 64));
    }

    @Test
    void rejectsWhitespaceMistakesControlCharactersAndBadLengths() {
        assertFalse(PasscodePolicy.isValid(" abc", 4, 64));
        assertFalse(PasscodePolicy.isValid("abc ", 4, 64));
        assertFalse(PasscodePolicy.isValid("abc\n123", 4, 64));
        assertFalse(PasscodePolicy.isValid("abc", 4, 64));
        assertFalse(PasscodePolicy.isValid(null, 4, 64));
    }
}
