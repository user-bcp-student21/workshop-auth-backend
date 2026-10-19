package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@ApplicationScoped
public class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 600_000;
    private static final String FORMAT = "pbkdf2-sha256$v1$" + ITERATIONS + "$";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BYTES = 32;
    private static final int FIELD_COUNT = 5;
    private static final String INVALID_FORMAT = "Invalid stored password format";
    private final SecureRandom random = new SecureRandom();

    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] derived = derive(password, salt);
        return FORMAT + encode(salt) + "$" + encode(derived);
    }

    public boolean verify(String password, String encoded) {
        HashParts parts = parse(encoded);
        return MessageDigest.isEqual(parts.derived(), derive(password, parts.salt()));
    }

    public void validate(String encoded) {
        parse(encoded);
    }

    private HashParts parse(String encoded) {
        if (encoded == null || !encoded.startsWith(FORMAT)) {
            throw new IllegalStateException(INVALID_FORMAT);
        }
        String[] fields = encoded.split("\\$", -1);
        if (fields.length != FIELD_COUNT) {
            throw new IllegalStateException(INVALID_FORMAT);
        }
        return new HashParts(decode(fields[3], SALT_BYTES), decode(fields[4], KEY_BYTES));
    }

    private byte[] decode(String encoded, int expectedBytes) {
        if (encoded.length() != ((expectedBytes + 2) / 3) * 4) {
            throw new IllegalStateException(INVALID_FORMAT);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException(INVALID_FORMAT);
        }
        if (bytes.length != expectedBytes || !encode(bytes).equals(encoded)) {
            throw new IllegalStateException(INVALID_FORMAT);
        }
        return bytes;
    }

    private byte[] derive(String password, byte[] salt) {
        char[] characters = password.toCharArray();
        PBEKeySpec specification = new PBEKeySpec(characters, salt, ITERATIONS, KEY_BYTES * Byte.SIZE);
        Arrays.fill(characters, '\0');
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(specification).getEncoded();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Password hashing is unavailable", error);
        } finally {
            specification.clearPassword();
        }
    }

    private String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private record HashParts(byte[] salt, byte[] derived) {}
}
