package com.bcp.workshop.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.runtime.Startup;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@ApplicationScoped
@Startup
public class SigningKeyProvider {
    private final String signingKeyPath;
    private PrivateKey privateKey;

    @Inject
    public SigningKeyProvider(@ConfigProperty(name = "auth.token.signing-key-path") String signingKeyPath) {
        this.signingKeyPath = signingKeyPath;
    }

    @PostConstruct
    void loadPrivateKey() {
        try {
            String pem = Files.readString(Path.of(signingKeyPath), StandardCharsets.UTF_8);
            String begin = "-----BEGIN PRIVATE KEY-----";
            String end = "-----END PRIVATE KEY-----";
            if (!pem.startsWith(begin) || !pem.trim().endsWith(end)) {
                throw new IllegalArgumentException("Expected a PKCS#8 PEM private key");
            }
            String encoded = pem.substring(begin.length(), pem.lastIndexOf(end)).replaceAll("\\s", "");
            PrivateKey parsed = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)));
            if (!(parsed instanceof RSAPrivateKey rsaKey) || rsaKey.getModulus().bitLength() < 2048) {
                throw new IllegalArgumentException("RSA signing key must be at least 2048 bits");
            }
            privateKey = parsed;
        } catch (IOException | GeneralSecurityException | IllegalArgumentException error) {
            throw new IllegalStateException("Token signing key is unavailable or invalid", error);
        }
    }

    public PrivateKey signingKey() {
        return privateKey;
    }
}
