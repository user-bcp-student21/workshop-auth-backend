package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationServiceTest {
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void authenticatesOnlyMatchingCredentials() {
        UserRecord user = new UserRecord(1, "fixture", "Fixture", "STUDENT");
        String encoded = hasher.hash("password");
        LegacyDatabase database = new LegacyDatabase("unused", new PasswordHashMigration(hasher)) {
            @Override
            public StoredCredentials findByUsername(String username) {
                return username.equals("fixture") ? new StoredCredentials(user, encoded) : null;
            }
        };
        AuthenticationService service = new AuthenticationService(database, hasher);
        assertNull(service.authenticate("missing", "password"));
        assertNull(service.authenticate("fixture", "wrong"));
        assertEquals(user, service.authenticate("fixture", "password"));
    }

    @Test
    void corruptHashesFailExplicitlyWithoutSensitiveDetails() {
        LegacyDatabase database = new LegacyDatabase("unused", new PasswordHashMigration(hasher)) {
            @Override
            public StoredCredentials findByUsername(String username) {
                return new StoredCredentials(new UserRecord(1, username, "Fixture", "STUDENT"), "sensitive-invalid");
            }
        };
        var service = new AuthenticationService(database, hasher);
        List<LogRecord> records = new ArrayList<>();
        Logger logger = Logger.getLogger(AuthenticationService.class.getName());
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        logger.addHandler(handler);
        try {
            var error = assertThrows(IllegalStateException.class, () -> service.authenticate("fixture", "secret-input"));
            assertEquals("Invalid stored password format", error.getMessage());
            assertFalse(records.isEmpty());
            StringWriter logged = new StringWriter();
            PrintWriter writer = new PrintWriter(logged);
            for (LogRecord record : records) {
                writer.println(record.getMessage());
                if (record.getThrown() != null) {
                    record.getThrown().printStackTrace(writer);
                }
            }
            assertFalse(logged.toString().contains("sensitive-invalid"));
            assertFalse(logged.toString().contains("secret-input"));
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    void storedCredentialsDoNotExposeHashesInDiagnosticStrings() {
        var credentials = new StoredCredentials(new UserRecord(1, "fixture", "Fixture", "STUDENT"), "private-hash");
        assertEquals("StoredCredentials[redacted]", credentials.toString());
    }
}
