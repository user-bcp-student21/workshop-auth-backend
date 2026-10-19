package com.bcp.workshop.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class LegacyDatabaseTest {
    private final PasswordHasher hasher = new PasswordHasher();
    private final LegacyDatabase database = new LegacyDatabase(new PasswordHashMigration(hasher));
    private final AuthenticationService service = new AuthenticationService(database, hasher);

    @BeforeEach
    void initialize() throws SQLException {
        try (Connection ignored = connection()) {
            // Load the JDBC driver in this test classloader before initialization.
        }
        database.initialize();
    }

    @Test
    void preservesExistingUsersOnInitialization() throws SQLException {
        insert("existing", "original", "<Existing>", "TRAINER");
        try {
            database.initialize();
            UserRecord user = service.authenticate("existing", "original");
            assertNotNull(user);
            assertEquals("<Existing>", user.displayName());
            assertEquals("TRAINER", user.role());
            assertNull(service.authenticate("existing", "different"));
        } finally {
            delete("existing");
        }
    }

    @Test
    void preservesQueryFailureAsMissingUser() throws SQLException {
        try (Connection connection = connection(); var statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE legacy_users RENAME TO temporarily_unavailable");
            try {
                assertNull(service.authenticate("student", "Workshop123!"));
            } finally {
                statement.executeUpdate("ALTER TABLE temporarily_unavailable RENAME TO legacy_users");
            }
        }
    }

    @Test
    void treatsQuotedCredentialsAsLiteralData() throws SQLException {
        insert("quote'user", "quote'password", "Quoted", "STUDENT");
        try {
            assertNotNull(service.authenticate("quote'user", "quote'password"));
            assertNull(service.authenticate("quote'user", "incorrect"));
        } finally {
            delete("quote'user");
        }
    }

    @Test
    void rejectsSqlConditionManipulation() {
        assertNull(service.authenticate("student' --", "incorrect"));
    }

    @Test
    void storesOnlyVersionedPasswordHashes() throws SQLException {
        try (Connection connection = connection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT password FROM legacy_users")) {
            while (rows.next()) {
                assertTrue(rows.getString(1).startsWith("pbkdf2-sha256$v1$600000$"),
                        "Active passwords must be versioned hashes");
            }
        }
    }

    static Connection connection() throws SQLException {
        String url = System.getenv("AUTH_DB_URL");
        if (url == null || !url.startsWith("jdbc:sqlite:./target/test-data/")) {
            throw new IllegalStateException("Tests require AUTH_DB_URL pointing to target/test-data");
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException error) {
            throw new IllegalStateException("SQLite test driver is unavailable", error);
        }
        return DriverManager.getConnection(url);
    }

    private void insert(String username, String password, String displayName, String role) throws SQLException {
        try (Connection connection = connection(); var statement = connection.prepareStatement(
                "INSERT INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)")) {
            statement.setString(1, username);
            statement.setString(2, hasher.hash(password));
            statement.setString(3, displayName);
            statement.setString(4, role);
            statement.executeUpdate();
        }
    }

    private void delete(String username) throws SQLException {
        try (Connection connection = connection(); var statement = connection.prepareStatement(
                "DELETE FROM legacy_users WHERE username=?")) {
            statement.setString(1, username);
            statement.executeUpdate();
        }
    }
}
