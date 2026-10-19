package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AuthenticationService {
    private static final Logger LOG = Logger.getLogger(AuthenticationService.class);
    private final LegacyDatabase database;
    private final PasswordHasher hasher;

    @Inject
    public AuthenticationService(LegacyDatabase database, PasswordHasher hasher) {
        this.database = database;
        this.hasher = hasher;
    }

    public UserRecord authenticate(String username, String password) {
        StoredCredentials credentials = database.findByUsername(username);
        if (credentials == null) {
            return null;
        }
        if (!matches(password, credentials.passwordHash())) {
            return null;
        }
        return credentials.user();
    }

    private boolean matches(String password, String encoded) {
        try {
            return hasher.verify(password, encoded);
        } catch (IllegalStateException error) {
            LOG.error("Stored credential verification failed", error);
            throw error;
        }
    }
}
