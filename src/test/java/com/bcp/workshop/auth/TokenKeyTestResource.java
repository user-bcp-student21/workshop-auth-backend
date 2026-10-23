package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

public class TokenKeyTestResource implements QuarkusTestResourceLifecycleManager {
    private Path directory;

    @Override
    public Map<String, String> start() {
        try {
            Path testData = Path.of("target", "test-data");
            Files.createDirectories(testData);
            directory = Files.createTempDirectory(testData, "token-key-");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            String privatePem = pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
            Path privateKeyPath = directory.resolve("private.pem");
            Files.writeString(privateKeyPath, privatePem, StandardCharsets.UTF_8);
            return Map.of(
                    "auth.token.signing-key-path", privateKeyPath.toString(),
                    "auth.token.test-public-key",
                    Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())
            );
        } catch (GeneralSecurityException | IOException error) {
            throw new IllegalStateException("Unable to create test signing keys", error);
        }
    }

    @Override
    public void stop() {
        if (directory == null) {
            return;
        }
        try {
            Files.deleteIfExists(directory.resolve("private.pem"));
            Files.deleteIfExists(directory);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to remove temporary test signing key", error);
        }
    }

    private String pem(String label, byte[] encoded) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(encoded)
                + "\n-----END " + label + "-----\n";
    }
}
