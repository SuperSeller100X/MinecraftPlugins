package dev.superseller.chestlock.security;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** PBKDF2-HMAC-SHA256 hashing. Plaintext passcodes are never persisted or logged. */
public final class PasscodeHasher {
    static final int SALT_BYTES = 16;
    static final int HASH_BITS = 256;
    private final SecureRandom random;

    public PasscodeHasher() {
        this(new SecureRandom());
    }

    PasscodeHasher(SecureRandom random) {
        this.random = random;
    }

    public PasswordHash hash(char[] passcode, int iterations) throws GeneralSecurityException {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return new PasswordHash(salt, derive(passcode, salt, iterations));
    }

    public boolean verify(char[] passcode, byte[] salt, byte[] expectedHash, int iterations)
            throws GeneralSecurityException {
        byte[] actual = derive(passcode, salt, iterations);
        try {
            return MessageDigest.isEqual(actual, expectedHash);
        } finally {
            Arrays.fill(actual, (byte) 0);
        }
    }

    private byte[] derive(char[] passcode, byte[] salt, int iterations) throws GeneralSecurityException {
        if (iterations < 1) {
            throw new IllegalArgumentException("iterations must be positive");
        }
        PBEKeySpec spec = new PBEKeySpec(passcode, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
            Arrays.fill(passcode, '\0');
        }
    }

    public record PasswordHash(byte[] salt, byte[] hash) {
        public PasswordHash {
            salt = salt.clone();
            hash = hash.clone();
        }

        @Override
        public byte[] salt() {
            return salt.clone();
        }

        @Override
        public byte[] hash() {
            return hash.clone();
        }
    }
}
