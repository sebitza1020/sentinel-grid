package com.defense.sentinel;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * @QuarkusTest for the /api/geofences contract — also proves the app still boots with the new
 * geofencing/navigation endpoints (datasource/Hibernate inactive under %test).
 */
@QuarkusTest
class GeofenceResourceTest {

  @Test
  void syncsAndListsZones() {
    String body =
        "[{\"id\":\"nfz-1\",\"polygon\":[[44.4,26.0],[44.4,26.2],[44.5,26.2],[44.5,26.0]]}]";

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/api/geofences")
        .then()
        .statusCode(200)
        .body("$", hasSize(1))
        .body("[0].id", is("nfz-1"));

    given()
        .when()
        .get("/api/geofences")
        .then()
        .statusCode(200)
        .body("[0].id", is("nfz-1"));
  }
}
