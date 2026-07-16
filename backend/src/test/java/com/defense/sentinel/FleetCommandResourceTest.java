package com.defense.sentinel;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.defense.sentinel.service.IntelligenceService;
import com.defense.sentinel.service.VoiceCommandParsingException;
import com.defense.sentinel.service.VoiceFleetCommandService;
import com.defense.sentinel.websocket.TelemetrySocket;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.util.KeyUtils;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FleetCommandResourceTest {

  @InjectMock IntelligenceService intelligenceService;

  @InjectMock VoiceFleetCommandService commandService;

  @InjectMock TelemetrySocket telemetrySocket;

  @Test
  void acceptsRawTextAndReturnsExecutedCommand() {
    VoiceCommandIntent intent = new VoiceCommandIntent("MOVE", "RAZOR-12", 44.4268, 26.1042);
    when(telemetrySocket.currentStates())
        .thenReturn(Map.of("RAZOR-12", Map.of("lat", 44.4, "lng", 26.1)));
    when(intelligenceService.parseVoiceCommand(anyString(), any())).thenReturn(intent);
    when(commandService.execute(intent))
        .thenReturn(
            new VoiceCommandResponse(
                "MOVE",
                "RAZOR-12",
                44.4268,
                26.1042,
                List.of(new double[] {44.4268, 26.1042, 100})));

    given()
        .auth()
        .oauth2(commanderToken())
        .contentType("text/plain")
        .body("Send Razor-12 to the center of Bucharest")
        .when()
        .post("/api/fleet/command-voice")
        .then()
        .statusCode(200)
        .body("action", is("MOVE"))
        .body("callSign", is("RAZOR-12"))
        .body("waypoints.size()", is(1));
  }

  @Test
  void mapsInvalidTranscriptAndAiFailuresToStructuredErrors() {
    given()
        .auth()
        .oauth2(token("commander", "COMMANDER"))
        .contentType("text/plain")
        .body(" ")
        .when()
        .post("/api/fleet/command-voice")
        .then()
        .statusCode(400)
        .body("code", is("INVALID_TRANSCRIPT"));

    when(telemetrySocket.currentStates()).thenReturn(Map.of("RAZOR-12", Map.of()));
    when(intelligenceService.parseVoiceCommand(anyString(), any()))
        .thenThrow(
            new VoiceCommandParsingException(
                VoiceCommandParsingException.Kind.INVALID_COMMAND, "invalid"));
    given()
        .auth()
        .oauth2(token("commander", "COMMANDER"))
        .contentType("text/plain")
        .body("do something")
        .when()
        .post("/api/fleet/command-voice")
        .then()
        .statusCode(422)
        .body("code", is("INVALID_VOICE_COMMAND"));

    doThrow(
            new VoiceCommandParsingException(
                VoiceCommandParsingException.Kind.UPLINK_FAILURE, "offline"))
        .when(intelligenceService)
        .parseVoiceCommand(anyString(), any());
    given()
        .auth()
        .oauth2(token("commander", "COMMANDER"))
        .contentType("text/plain")
        .body("move razor")
        .when()
        .post("/api/fleet/command-voice")
        .then()
        .statusCode(502)
        .body("code", is("AI_UPLINK_FAILURE"));
  }

  @Test
  void rejectsNonCommanderUsers() {
    given()
        .auth()
        .oauth2(token("operator", "USER"))
        .contentType("text/plain")
        .body("Move Razor-12")
        .when()
        .post("/api/fleet/command-voice")
        .then()
        .statusCode(403);
  }

  private static String token(String user, String role) {
    try (InputStream keyStream =
        FleetCommandResourceTest.class.getResourceAsStream("/privateKey.pem")) {
      if (keyStream == null) {
        throw new IllegalStateException("Test signing key is unavailable.");
      }
      String key = new String(keyStream.readAllBytes(), StandardCharsets.UTF_8);
      return Jwt.issuer("https://sentinel-grid.com")
          .upn(user)
          .groups(Set.of(role))
          .expiresIn(300)
          .sign(KeyUtils.decodePrivateKey(key));
    } catch (Exception e) {
      throw new IllegalStateException("Could not create a test JWT.", e);
    }
  }

  private static String commanderToken() {
    return given()
        .contentType("application/json")
        .body(Map.of("username", "commander", "password", "sentinel2025"))
        .when()
        .post("/api/auth/login")
        .then()
        .statusCode(200)
        .extract()
        .path("token");
  }
}
