package com.defense.sentinel;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;

import com.defense.sentinel.service.MissionDebriefService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * @QuarkusTest for the /api/analytics/export contract: PDF media type + attachment disposition.
 * The debrief service is mocked (WeatherResourceTest pattern) so no live pool/weather is needed;
 * also proves the app boots with OpenPDF on the classpath.
 */
@QuarkusTest
class AnalyticsResourceTest {

  @InjectMock MissionDebriefService missionDebriefService;

  @Test
  void exportsPdfAttachment() {
    when(missionDebriefService.generateDebriefPdf())
        .thenReturn("%PDF-1.4 stub".getBytes(StandardCharsets.US_ASCII));

    given()
        .when()
        .get("/api/analytics/export")
        .then()
        .statusCode(200)
        .contentType("application/pdf")
        .header("Content-Disposition", containsString("attachment"));
  }
}
