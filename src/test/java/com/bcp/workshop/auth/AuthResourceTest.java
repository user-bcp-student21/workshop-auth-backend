package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.sql.Connection;
import java.sql.SQLException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AuthResourceTest {
    @BeforeAll
    static void requireIsolatedDatabase() throws java.sql.SQLException {
        try (var ignored = LegacyDatabaseTest.connection()) {
            // Refuse to run HTTP tests against the developer's database.
        }
    }

    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("token", notNullValue());
    }

    @Test
    void preservesSuccessfulResponseAndLegacyToken() {
        for (String username : new String[]{"student", "trainer"}) {
            long before = Instant.now().getEpochSecond();
            var httpResponse = given().contentType("application/json")
                    .body(Map.of("username", username, "password",
                            username.equals("student") ? "Workshop123!" : "Trainer123!", "unknown", true))
                    .post("/auth/login").then().statusCode(200).extract().response();
            assertNull(httpResponse.getHeader("Set-Cookie"));
            var response = httpResponse.jsonPath();
            assertEquals(4, response.getMap("$").size());
            assertEquals(3600, response.getInt("expiresIn"));
            assertEquals("authenticated-with-legacy-flow", response.getString("debug"));
            assertEquals(username, response.getString("user.username"));
            assertEquals(username.toUpperCase(java.util.Locale.ROOT), response.getString("user.role"));
            assertEquals("Workshop " + (username.equals("student") ? "Student" : "Trainer"),
                    response.getString("user.displayName"));
            assertEquals(3, response.getMap("user").size());
            String[] token = new String(Base64.getDecoder().decode(response.getString("token")),
                    StandardCharsets.UTF_8).split("\\|");
            assertEquals(4, token.length);
            assertEquals(username, token[0]);
            assertEquals(response.getString("user.role"), token[1]);
            long expiry = Long.parseLong(token[2]);
            assertTrue(expiry >= before + 3600 && expiry <= Instant.now().getEpochSecond() + 3600);
        }
    }

    @Test
    void preservesValidationOrderAndMessages() {
        assertError("null", 400, "Request is null");
        for (String body : new String[]{"{}", "{\"username\":null}", "{\"username\":\"\"}", "{\"username\":\" \"}"}) {
            assertError(body, 400, "Username is required");
        }
        for (String body : new String[]{"{\"username\":\"student\"}",
                "{\"username\":\"student\",\"password\":null}",
                "{\"username\":\"student\",\"password\":\"\"}",
                "{\"username\":\"student\",\"password\":\"  \"}"}) {
            assertError(body, 400, "Password is required");
        }
        assertEquals(400, new AuthResource().login(null).getStatus());
    }

    @Test
    void rejectsMalformedJsonAndUnsupportedNegotiation() {
        given().contentType("application/json").accept("application/json").body("{")
                .post("/auth/login").then().statusCode(400).body(is(""));
        given().contentType("text/plain").accept("application/json").body("text")
                .post("/auth/login").then().statusCode(415).body(is(""));
        given().contentType("application/json").accept("text/plain").body("{}")
                .post("/auth/login").then().statusCode(406).body(is(""));
    }

    @Test
    void reportsCorruptStoredHashOverHttp() throws SQLException {
        logsInWithWorkshopUser();
        try (Connection connection = LegacyDatabaseTest.connection()) {
            String original = readStudentHash(connection);
            try {
                updateStudentHash(connection, "invalid-test-hash");
                given().contentType("application/json")
                        .body(Map.of("username", "student", "password", "Workshop123!"))
                        .post("/auth/login").then().statusCode(500)
                        .body("message", is("Invalid stored password format"))
                        .body("exception", is(IllegalStateException.class.getName()));
            } finally {
                updateStudentHash(connection, original);
            }
        }
    }

    @Test
    void characterizesDocumentedQueryFailureOverHttp() throws SQLException {
        logsInWithWorkshopUser();
        try (Connection connection = LegacyDatabaseTest.connection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE legacy_users RENAME TO http_unavailable_users");
            try {
                assertError("{\"username\":\"student\",\"password\":\"Workshop123!\"}",
                        401, "Invalid user student or password");
            } finally {
                statement.executeUpdate("ALTER TABLE http_unavailable_users RENAME TO legacy_users");
            }
        }
    }

    private String readStudentHash(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT password FROM legacy_users WHERE username=?")) {
            statement.setString(1, "student");
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getString(1);
            }
        }
    }

    private void updateStudentHash(Connection connection, String hash) throws SQLException {
        try (var statement = connection.prepareStatement("UPDATE legacy_users SET password=? WHERE username=?")) {
            statement.setString(1, hash);
            statement.setString(2, "student");
            assertEquals(1, statement.executeUpdate());
        }
    }

    @Test
    void preservesInvalidCredentialsWithoutTrimming() {
        for (String username : new String[]{"missing", " student", "student "}) {
            assertError("{\"username\":\"" + username + "\",\"password\":\"Workshop123!\"}",
                    401, "Invalid user " + username + " or password");
        }
        for (String password : new String[]{"wrong", " Workshop123!", "Workshop123! "}) {
            assertError("{\"username\":\"student\",\"password\":\"" + password + "\"}",
                    401, "Invalid user student or password");
        }
    }

    @Test
    void preservesEscapingAndInternalErrorShape() {
        AuthResource resource = resourceWithService(new AuthenticationService(null, null) {
            @Override
            public UserRecord authenticate(String username, String password) {
                return new UserRecord(99, username, "<Trainer & Student>", "STUDENT");
            }
        });
        var request = new LoginRequest();
        request.username = "fixture";
        request.password = "fixture-password";
        Map<?, ?> body = assertInstanceOf(Map.class, resource.login(request).getEntity());
        Map<?, ?> user = assertInstanceOf(Map.class, body.get("user"));
        assertEquals("&lt;Trainer &amp; Student&gt;", user.get("displayName"));
        assertEquals(3, user.size());
        for (String message : new String[]{"controlled failure", null}) {
            resource = resourceWithService(new AuthenticationService(null, null) {
                @Override
                public UserRecord authenticate(String username, String password) {
                    throw new IllegalStateException(message);
                }
            });
            var failure = resource.login(request);
            assertEquals(500, failure.getStatus());
            assertEquals(Map.of("message", message == null ? "Unknown error" : message,
                    "exception", IllegalStateException.class.getName()), failure.getEntity());
        }
    }

    private AuthResource resourceWithService(AuthenticationService service) {
        AuthResource resource = new AuthResource();
        resource.authenticationService = service;
        return resource;
    }

    private void assertError(String body, int status, String message) {
        given().contentType("application/json").body(body).post("/auth/login")
                .then().statusCode(status).body("message", is(message));
    }
}
