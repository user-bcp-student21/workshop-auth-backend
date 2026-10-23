package com.bcp.workshop.auth;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SignedTokenService {
    public static final int TOKEN_LIFETIME_SECONDS = 3600;

    private final SigningKeyProvider signingKeyProvider;

    @Inject
    public SignedTokenService(SigningKeyProvider signingKeyProvider) {
        this.signingKeyProvider = signingKeyProvider;
    }

    public String create(String username, String role) {
        return Jwt.claims()
                .claim("sub", username)
                .claim("role", role)
                .expiresIn(TOKEN_LIFETIME_SECONDS)
                .sign(signingKeyProvider.signingKey());
    }
}
