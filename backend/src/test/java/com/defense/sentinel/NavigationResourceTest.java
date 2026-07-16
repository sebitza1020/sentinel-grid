package com.defense.sentinel;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import com.defense.sentinel.service.GeofenceService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class NavigationResourceTest {

  @Inject GeofenceService geofenceService;

  @BeforeEach
  void resetZones() {
    geofenceService.clear();
  }

  @Test
  void returnsThreeDimensionalWaypointsForClearRoutes() {
    given()
        .contentType("application/json")
        .body(
            """
            {
              "callSign": "FALCON-1",
              "start": [44.4, 26.1, 150],
              "end": [44.5, 26.2, 200]
            }
            """)
        .when()
        .post("/api/navigation/route")
        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("[0][2]", is(200.0F));
  }

  @Test
  void returnsStructuredErrorsForInvalidAndBlockedRoutes() {
    given()
        .contentType("application/json")
        .body("{\"callSign\":\"FALCON-1\",\"start\":[44.4],\"end\":[44.5,26.2,100]}")
        .when()
        .post("/api/navigation/route")
        .then()
        .statusCode(400)
        .body("code", is("INVALID_ROUTE"));

    geofenceService.replaceAll(
        java.util.List.of(
            new Geofence(
                "nfz",
                new double[][] {
                  {44.39, 26.09}, {44.39, 26.11}, {44.41, 26.11}, {44.41, 26.09}
                },
                0,
                500)));

    given()
        .contentType("application/json")
        .body(
            """
            {
              "callSign": "FALCON-1",
              "start": [44.4, 26.1, 100],
              "end": [44.5, 26.2, 100]
            }
            """)
        .when()
        .post("/api/navigation/route")
        .then()
        .statusCode(409)
        .body("code", is("ROUTE_BLOCKED"));
  }
}
