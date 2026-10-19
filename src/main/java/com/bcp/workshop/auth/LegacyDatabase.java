package com.bcp.workshop.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

@ApplicationScoped
public class LegacyDatabase {
    private static final String DB_URL = System.getenv().getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");
    private static final Logger LOG = Logger.getLogger(LegacyDatabase.class);
    private static final String FIND_USER =
            "SELECT id,username,display_name,role,password FROM legacy_users WHERE username=?";
    private final String url;
    private final PasswordHashMigration migration;
    private boolean ready;

    @Inject
    public LegacyDatabase(PasswordHashMigration migration) {
        this(DB_URL, migration);
    }

    LegacyDatabase(String url, PasswordHashMigration migration) {
        this.url = url;
        this.migration = migration;
    }

    @PostConstruct
    void initialize() {
        ready = false;
        try {
            Files.createDirectories(Path.of("data"));
            try (Connection connection = DriverManager.getConnection(url)) {
                migration.migrate(connection);
            }
            ready = true;
        } catch (IOException | SQLException | IllegalStateException error) {
            LOG.error("Authentication database initialization failed", error);
            throw new IllegalStateException("Authentication database initialization failed", error);
        }
    }

    public StoredCredentials findByUsername(String username) {
        if (!ready) {
            throw new IllegalStateException("Authentication database is not initialized");
        }
        try (Connection connection = DriverManager.getConnection(url);
             var statement = connection.prepareStatement(FIND_USER)) {
            statement.setString(1, username);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? mapCredentials(rows) : null;
            }
        } catch (SQLException error) {
            LOG.error("Credential query failed", error);
            return null;
        }
    }

    private StoredCredentials mapCredentials(ResultSet rows) throws SQLException {
        UserRecord user = new UserRecord(rows.getLong("id"), rows.getString("username"),
                rows.getString("display_name"), rows.getString("role"));
        return new StoredCredentials(user, rows.getString("password"));
    }
}
