package dev.superseller.chestlock.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class PasscodeHasherTest {
    @Test
    void hashesAndVerifiesWithoutPersistingPlaintext() throws Exception {
        PasscodeHasher hasher = new PasscodeHasher();
        PasscodeHasher.PasswordHash first = hasher.hash("correct horse".toCharArray(), 10_000);
        PasscodeHasher.PasswordHash second = hasher.hash("correct horse".toCharArray(), 10_000);

        assertTrue(hasher.verify("correct horse".toCharArray(), first.salt(), first.hash(), 10_000));
        assertFalse(hasher.verify("wrong battery".toCharArray(), first.salt(), first.hash(), 10_000));
        assertNotEquals(Base64.getEncoder().encodeToString(first.hash()),
                Base64.getEncoder().encodeToString(second.hash()), "random salts must produce different hashes");
    }
}
