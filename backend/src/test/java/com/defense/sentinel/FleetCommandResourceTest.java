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
import io.quarkus.test.security.TestSecurity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FleetCommandResourceTest {

  @InjectMock IntelligenceService intelligenceService;

  @InjectMock VoiceFleetCommandService commandService;

  @InjectMock TelemetrySocket telemetrySocket;

  @Test
  @TestSecurity(user = "commander", roles = "COMMANDER")
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
  @TestSecurity(user = "commander", roles = "COMMANDER")
  void mapsInvalidTranscriptAndAiFailuresToStructuredErrors() {
    given()
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
        .contentType("text/plain")
        .body("move razor")
        .when()
        .post("/api/fleet/command-voice")
        .then()
        .statusCode(502)
        .body("code", is("AI_UPLINK_FAILURE"));
  }

  @Test
  @TestSecurity(user = "operator", roles = "USER")
  void rejectsNonCommanderUsers() {
    given()
        .contentType("text/plain")
        .body("Move Razor-12")
        .when()
        .post("/api/fleet/command-voice")
        .then()
        .statusCode(403);
  }
}
