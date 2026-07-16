package com.defense.sentinel;

import com.defense.sentinel.service.IntelligenceService;
import com.defense.sentinel.service.VoiceCommandExecutionException;
import com.defense.sentinel.service.VoiceCommandParsingException;
import com.defense.sentinel.service.VoiceFleetCommandService;
import com.defense.sentinel.websocket.TelemetrySocket;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

/** Commander voice-command ingress. */
@Path("/api/fleet")
@Produces(MediaType.APPLICATION_JSON)
public class FleetCommandResource {

  @Inject IntelligenceService intelligenceService;

  @Inject VoiceFleetCommandService commandService;

  @Inject TelemetrySocket telemetrySocket;

  @POST
  @Path("/command-voice")
  @Consumes(MediaType.TEXT_PLAIN)
  @RolesAllowed("COMMANDER")
  public Response commandVoice(String transcript) {
    if (transcript == null || transcript.isBlank()) {
      return error(400, "INVALID_TRANSCRIPT", "A non-empty voice transcript is required.");
    }

    try {
      VoiceCommandIntent intent =
          intelligenceService.parseVoiceCommand(
              transcript.trim(), telemetrySocket.currentStates().keySet());
      return Response.ok(commandService.execute(intent)).build();
    } catch (VoiceCommandParsingException e) {
      int status =
          e.kind() == VoiceCommandParsingException.Kind.UPLINK_FAILURE ? 502 : 422;
      String code =
          e.kind() == VoiceCommandParsingException.Kind.UPLINK_FAILURE
              ? "AI_UPLINK_FAILURE"
              : "INVALID_VOICE_COMMAND";
      return error(status, code, e.getMessage());
    } catch (VoiceCommandExecutionException e) {
      int status =
          e.kind() == VoiceCommandExecutionException.Kind.DRONE_NOT_FOUND ? 404 : 409;
      return error(status, e.code(), e.getMessage());
    }
  }

  private static Response error(int status, String code, String message) {
    return Response.status(status).entity(Map.of("code", code, "message", message)).build();
  }
}
