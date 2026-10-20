package com.bcp.workshop.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SignedTokenServiceTest {
    @Test
    void createsVerifiableTokenWithIdentityAndOneHourExpiry() throws Exception {
        Path directory = createTestDirectory();
        try {
            KeyPair keyPair = generateRsaKeyPair(2048);
            Path privateKeyPath = directory.resolve("private.pem");
            Files.writeString(privateKeyPath, pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
            SigningKeyProvider provider = new SigningKeyProvider(privateKeyPath.toString());
            provider.loadPrivateKey();

            long before = System.currentTimeMillis() / 1000;
            String token = new SignedTokenService(provider).create("student", "STUDENT");
            long after = System.currentTimeMillis() / 1000;
            String[] parts = token.split("\\.", -1);
            assertEquals(3, parts.length);

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> header = mapper.readValue(
                    Base64.getUrlDecoder().decode(parts[0]), new TypeReference<>() {});
            Map<String, Object> claims = mapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]), new TypeReference<>() {});
            assertEquals("RS256", header.get("alg"));
            assertEquals("JWT", header.get("typ"));
            assertEquals("student", claims.get("sub"));
            assertEquals("STUDENT", claims.get("role"));
            long expiry = ((Number) claims.get("exp")).longValue();
            assertTrue(expiry >= before + 3600 && expiry <= after + 3600);

            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(keyPair.getPublic());
            verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            assertTrue(verifier.verify(Base64.getUrlDecoder().decode(parts[2])));
        } finally {
            deleteTestDirectory(directory, "private.pem");
        }
    }

    @Test
    void rejectsMissingMalformedAndWeakPrivateKeys() throws Exception {
        Path directory = createTestDirectory();
        try {
            Path missing = directory.resolve("missing.pem");
            assertThrows(IllegalStateException.class,
                    () -> new SigningKeyProvider(missing.toString()).loadPrivateKey());

            Path malformed = directory.resolve("malformed.pem");
            Files.writeString(malformed, "not a PEM private key");
            assertThrows(IllegalStateException.class,
                    () -> new SigningKeyProvider(malformed.toString()).loadPrivateKey());

            KeyPair weakKey = generateRsaKeyPair(1024);
            Path weak = directory.resolve("weak.pem");
            Files.writeString(weak, pem("PRIVATE KEY", weakKey.getPrivate().getEncoded()));
            assertThrows(IllegalStateException.class,
                    () -> new SigningKeyProvider(weak.toString()).loadPrivateKey());
        } finally {
            deleteTestDirectory(directory, "malformed.pem", "weak.pem");
        }
    }

    private Path createTestDirectory() throws IOException {
        Path testData = Path.of("target", "test-data");
        Files.createDirectories(testData);
        return Files.createTempDirectory(testData, "signed-token-");
    }

    private KeyPair generateRsaKeyPair(int bits) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair();
    }

    private String pem(String label, byte[] encoded) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(encoded)
                + "\n-----END " + label + "-----\n";
    }

    private void deleteTestDirectory(Path directory, String... files) throws IOException {
        for (String file : files) {
            Files.deleteIfExists(directory.resolve(file));
        }
        Files.deleteIfExists(directory);
    }
}
