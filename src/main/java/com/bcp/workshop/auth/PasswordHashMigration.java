package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class PasswordHashMigration {
    private static final int CURRENT_VERSION = 1;
    private static final String CREATE_USERS = "CREATE TABLE IF NOT EXISTS legacy_users "
            + "(id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL,"
            + "display_name TEXT NOT NULL, role TEXT NOT NULL)";
    private static final String CREATE_VERSION = "CREATE TABLE IF NOT EXISTS auth_schema_version "
            + "(id INTEGER PRIMARY KEY CHECK(id=1), version INTEGER NOT NULL)";
    private static final String READ_VERSION = "SELECT version FROM auth_schema_version WHERE id=1";
    private static final String WRITE_VERSION = "INSERT INTO auth_schema_version(id,version) VALUES (1,?)";
    private static final String READ_PASSWORDS = "SELECT id,password FROM legacy_users";
    private static final String UPDATE_PASSWORD = "UPDATE legacy_users SET password=? WHERE id=?";
    private static final String FIND_USER = "SELECT 1 FROM legacy_users WHERE username=?";
    private static final String INSERT_USER =
            "INSERT INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)";
    private final PasswordHasher hasher;

    @Inject
    public PasswordHashMigration(PasswordHasher hasher) {
        this.hasher = hasher;
    }

    public void migrate(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try {
            createSchema(connection);
            int version = readVersion(connection);
            if (version == 0) {
                migrateLegacyPasswords(connection);
                writeVersion(connection);
            } else if (version != CURRENT_VERSION) {
                throw new IllegalStateException("Unsupported authentication schema version");
            }
            seedUsers(connection);
            validatePasswords(connection);
            connection.commit();
        } catch (SQLException | RuntimeException error) {
            rollback(connection, error);
            throw new IllegalStateException("Password migration failed", error);
        }
        connection.setAutoCommit(true);
    }

    private void createSchema(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_USERS);
            statement.executeUpdate(CREATE_VERSION);
        }
    }

    private int readVersion(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(READ_VERSION); var rows = statement.executeQuery()) {
            return rows.next() ? rows.getInt("version") : 0;
        }
    }

    private void writeVersion(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(WRITE_VERSION)) {
            statement.setInt(1, CURRENT_VERSION);
            statement.executeUpdate();
        }
    }

    private void migrateLegacyPasswords(Connection connection) throws SQLException {
        List<LegacyPassword> passwords = readLegacyPasswords(connection);
        try (var statement = connection.prepareStatement(UPDATE_PASSWORD)) {
            for (LegacyPassword password : passwords) {
                statement.setString(1, hasher.hash(password.value()));
                statement.setLong(2, password.id());
                statement.executeUpdate();
            }
        }
    }

    private List<LegacyPassword> readLegacyPasswords(Connection connection) throws SQLException {
        List<LegacyPassword> passwords = new ArrayList<>();
        try (var statement = connection.prepareStatement(READ_PASSWORDS); var rows = statement.executeQuery()) {
            while (rows.next()) {
                passwords.add(new LegacyPassword(rows.getLong("id"), rows.getString("password")));
            }
        }
        return passwords;
    }

    private void seedUsers(Connection connection) throws SQLException {
        seedUser(connection, "student", "Workshop123!", "Workshop Student", "STUDENT");
        seedUser(connection, "trainer", "Trainer123!", "Workshop Trainer", "TRAINER");
    }

    private void seedUser(Connection connection, String username, String password,
                          String displayName, String role) throws SQLException {
        if (userExists(connection, username)) {
            return;
        }
        try (var statement = connection.prepareStatement(INSERT_USER)) {
            statement.setString(1, username);
            statement.setString(2, hasher.hash(password));
            statement.setString(3, displayName);
            statement.setString(4, role);
            statement.executeUpdate();
        }
    }

    private boolean userExists(Connection connection, String username) throws SQLException {
        try (var statement = connection.prepareStatement(FIND_USER)) {
            statement.setString(1, username);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private void validatePasswords(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(READ_PASSWORDS); var rows = statement.executeQuery()) {
            while (rows.next()) {
                hasher.validate(rows.getString("password"));
            }
        }
    }

    private void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
            connection.setAutoCommit(true);
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }

    private record LegacyPassword(long id, String value) {
        @Override
        public String toString() {
            return "LegacyPassword[redacted]";
        }
    }
}
