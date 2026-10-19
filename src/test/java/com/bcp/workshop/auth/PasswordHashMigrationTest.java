package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHashMigrationTest {
    Path directory;
    private final PasswordHasher hasher = new PasswordHasher();

    @BeforeEach
    void createIsolatedDirectory() throws IOException {
        Files.createDirectories(Path.of("target", "test-data"));
        directory = Files.createTempDirectory(Path.of("target", "test-data"), "migration-");
    }

    @AfterEach
    void cleanFixtures() throws IOException {
        Files.deleteIfExists(directory.resolve("migration.db"));
        Files.deleteIfExists(directory.resolve("blocked.db"));
        Files.delete(directory);
    }

    @Test
    void migratesLegacyRowsIncludingHashLikePasswordsAndIsIdempotent() throws SQLException {
        try (Connection connection = open()) {
            createLegacy(connection);
            insert(connection, "existing", "pbkdf2-sha256$v1$not-a-hash");
            PasswordHashMigration migration = new PasswordHashMigration(hasher);
            migration.migrate(connection);
            String first = password(connection, "existing");
            assertTrue(hasher.verify("pbkdf2-sha256$v1$not-a-hash", first));
            assertTrue(hasher.verify("Workshop123!", password(connection, "student")));
            assertTrue(hasher.verify("Trainer123!", password(connection, "trainer")));
            migration.migrate(connection);
            assertEquals(first, password(connection, "existing"));
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT id,display_name,role FROM legacy_users WHERE username='existing'")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getLong("id"));
                assertEquals("<Existing>", rows.getString("display_name"));
                assertEquals("TRAINER", rows.getString("role"));
            }
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void createsFreshDatabaseWithOnlyHashedSeeds() throws SQLException {
        try (Connection connection = open()) {
            new PasswordHashMigration(hasher).migrate(connection);
            assertTrue(hasher.verify("Workshop123!", password(connection, "student")));
            assertTrue(hasher.verify("Trainer123!", password(connection, "trainer")));
        }
    }

    @Test
    void rollsBackAllRowsAndVersionOnUpdateFailure() throws SQLException {
        try (Connection connection = open()) {
            createLegacy(connection);
            insert(connection, "first", "first-password");
            insert(connection, "second", "second-password");
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TRIGGER fail_migration BEFORE UPDATE ON legacy_users "
                        + "WHEN OLD.username='second' BEGIN SELECT RAISE(ABORT,'controlled migration failure'); END");
            }
            assertThrows(IllegalStateException.class, () -> new PasswordHashMigration(hasher).migrate(connection));
            assertEquals("first-password", password(connection, "first"));
            assertEquals("second-password", password(connection, "second"));
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT count(*) FROM sqlite_master WHERE name='auth_schema_version'")) {
                assertEquals(0, rows.getInt(1));
            }
            assertTrue(connection.getAutoCommit());
        }
    }

    @Test
    void refusesCorruptOrUnsupportedMigratedDatabase() throws SQLException {
        try (Connection connection = open()) {
            PasswordHashMigration migration = new PasswordHashMigration(hasher);
            migration.migrate(connection);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE legacy_users SET password='corrupt' WHERE username='student'");
            }
            assertThrows(IllegalStateException.class, () -> migration.migrate(connection));
            assertEquals("corrupt", password(connection, "student"));
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE auth_schema_version SET version=999");
            }
            assertThrows(IllegalStateException.class, () -> migration.migrate(connection));
        }
    }

    @Test
    void failedInitializationBlocksCredentialQueries() throws SQLException {
        String url = "jdbc:sqlite:" + directory.resolve("blocked.db");
        try (Connection connection = DriverManager.getConnection(url)) {
            createLegacy(connection);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("DROP TABLE legacy_users");
                statement.executeUpdate("CREATE TABLE legacy_users (broken TEXT)");
            }
        }
        LegacyDatabase database = new LegacyDatabase(url, new PasswordHashMigration(hasher));
        assertThrows(IllegalStateException.class, database::initialize);
        assertThrows(IllegalStateException.class, () -> database.findByUsername("student"));
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("migration.db"));
    }

    private void createLegacy(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "username TEXT UNIQUE,password TEXT NOT NULL,display_name TEXT NOT NULL,role TEXT NOT NULL)");
        }
    }

    private void insert(Connection connection, String username, String password) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO legacy_users(username,password,display_name,role) VALUES (?,?,'<Existing>','TRAINER')")) {
            statement.setString(1, username);
            statement.setString(2, password);
            statement.executeUpdate();
        }
    }

    private String password(Connection connection, String username) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT password FROM legacy_users WHERE username=?")) {
            statement.setString(1, username);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getString(1);
            }
        }
    }
}
