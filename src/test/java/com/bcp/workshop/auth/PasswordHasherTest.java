package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void usesSlowVersionedHashesAndDistinctSalts() {
        long started = System.nanoTime();
        String first = hasher.hash("same-test-password");
        System.out.printf("PBKDF2 600000 iterations: %.1f ms%n", (System.nanoTime() - started) / 1_000_000.0);
        String second = hasher.hash("same-test-password");
        assertNotEquals(first, second);
        String[] fields = first.split("\\$");
        assertEquals("pbkdf2-sha256", fields[0]);
        assertEquals("v1", fields[1]);
        assertEquals("600000", fields[2]);
        assertEquals(16, Base64.getDecoder().decode(fields[3]).length);
        assertEquals(32, Base64.getDecoder().decode(fields[4]).length);
        assertTrue(hasher.verify("same-test-password", first));
        assertFalse(hasher.verify("different", first));
    }

    @Test
    void preservesUnicodeWhitespaceAndLongPasswords() {
        for (String password : new String[]{"  spaced  ", "\u00f1\u6f22\uD83D\uDE00", "x".repeat(1024)}) {
            String encoded = hasher.hash(password);
            assertTrue(hasher.verify(password, encoded));
            assertFalse(hasher.verify(password + "x", encoded));
        }
    }

    @Test
    void rejectsMalformedUnsupportedAndNonCanonicalHashes() {
        String valid = hasher.hash("fixture");
        String[] fields = valid.split("\\$");
        for (String invalid : new String[]{"plain", "", valid + "$extra",
                valid.replace("v1", "v2"), valid.replace("sha256", "sha1"),
                valid.replace("600000", "1"), valid.replace("600000", "999999999"),
                "pbkdf2-sha256$v1$600000$!$!",
                "pbkdf2-sha256$v1$600000$YQ==$" + fields[4],
                "pbkdf2-sha256$v1$600000$" + fields[3] + "$YQ==",
                valid.replace(fields[3], fields[3].replace("=", ""))}) {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> hasher.verify("fixture", invalid));
            assertEquals("Invalid stored password format", error.getMessage());
        }
        assertThrows(IllegalStateException.class, () -> hasher.validate(null));
    }
}
