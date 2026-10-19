package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class HealthResourceTest {
    @Test
    void reportsHealthyWithoutAuthenticationOrRequestBody() {
        given().accept("application/json").get("/q/health")
                .then().statusCode(200).contentType("application/json")
                .body("$", aMapWithSize(2))
                .body("status", is("UP")).body("checks", empty());
    }

    @Test
    void acceptsOmittedOptionalAcceptHeaderRepeatedly() {
        for (int request = 0; request < 2; request++) {
            given().get("/q/health").then().statusCode(200)
                    .body("status", is("UP")).body("checks", empty());
        }
    }
}
