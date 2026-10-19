package com.bcp.workshop.auth;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@TestProfile(HealthDownTest.DownProfile.class)
class HealthDownTest {
    public static class DownProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("testing.health.failure", "true");
        }
    }

    @Test
    void reportsServiceUnavailableWhenAHealthCheckIsDown() {
        given().accept("application/json").get("/q/health")
                .then().statusCode(503).contentType("application/json")
                .body("$", aMapWithSize(2))
                .body("status", is("DOWN")).body("checks", hasSize(1))
                .body("checks[0].name", is("controlled-test-readiness"))
                .body("checks[0].status", is("DOWN"));
    }
}

@Readiness
@ApplicationScoped
@IfBuildProperty(name = "testing.health.failure", stringValue = "true")
class ControlledDownHealthCheck implements HealthCheck {
    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.down("controlled-test-readiness");
    }
}
