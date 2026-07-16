package com.defense.sentinel.service;

import com.defense.sentinel.VoiceCommandIntent;
import com.defense.sentinel.VoiceCommandResponse;
import com.defense.sentinel.websocket.TelemetrySocket;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Executes validated voice orders against the live fleet pool. */
@ApplicationScoped
public class VoiceFleetCommandService {

  @Inject TelemetrySocket telemetrySocket;

  @Inject NavigationService navigationService;

  public VoiceCommandResponse execute(VoiceCommandIntent intent) {
    Map.Entry<String, Map<String, Object>> target =
        telemetrySocket.currentStates().entrySet().stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase(intent.callSign))
            .findFirst()
            .orElseThrow(
                () ->
                    new VoiceCommandExecutionException(
                        VoiceCommandExecutionException.Kind.DRONE_NOT_FOUND,
                        "DRONE_NOT_LIVE",
                        "The requested drone is not present in the live fleet."));

    String callSign = target.getKey();
    Map<String, Object> state = target.getValue();
    String status = String.valueOf(state.getOrDefault("status", "")).toUpperCase();
    if ("RTB".equals(status) || "LANDED".equals(status)) {
      throw new VoiceCommandExecutionException(
          VoiceCommandExecutionException.Kind.COMMAND_CONFLICT,
          "AUTONOMOUS_OVERRIDE",
          callSign + " is under an autonomous " + status + " override.");
    }

    Double latitude = asDouble(state.get("lat"));
    Double longitude = asDouble(state.get("lng"));
    if (!validPosition(latitude, longitude)) {
      throw new VoiceCommandExecutionException(
          VoiceCommandExecutionException.Kind.COMMAND_CONFLICT,
          "POSITION_UNAVAILABLE",
          callSign + " has no live position and cannot be routed.");
    }
    double altitude = Math.max(0.0, firstDouble(state.get("alt"), state.get("altitude"), 0.0));

    final List<double[]> route;
    try {
      route =
          navigationService.route(
              new double[] {latitude, longitude, altitude},
              new double[] {intent.latitude, intent.longitude, altitude});
    } catch (RouteBlockedException e) {
      throw new VoiceCommandExecutionException(
          VoiceCommandExecutionException.Kind.COMMAND_CONFLICT,
          "ROUTE_BLOCKED",
          e.getMessage());
    }

    telemetrySocket.broadcastPath(callSign, route);
    Map<String, Object> targetState = new HashMap<>();
    targetState.put("status", "EN_ROUTE");
    targetState.put("target_lat", intent.latitude);
    targetState.put("target_lng", intent.longitude);
    targetState.put("target_alt", altitude);
    telemetrySocket.updateAndBroadcast(callSign, targetState);

    return new VoiceCommandResponse(
        "MOVE", callSign, intent.latitude, intent.longitude, route);
  }

  private static Double asDouble(Object value) {
    return value instanceof Number number ? number.doubleValue() : null;
  }

  private static double firstDouble(Object primary, Object secondary, double fallback) {
    Double value = asDouble(primary);
    if (value == null) {
      value = asDouble(secondary);
    }
    return value == null || !Double.isFinite(value) ? fallback : value;
  }

  private static boolean validPosition(Double latitude, Double longitude) {
    return latitude != null
        && longitude != null
        && Double.isFinite(latitude)
        && Double.isFinite(longitude)
        && latitude >= -90
        && latitude <= 90
        && longitude >= -180
        && longitude <= 180;
  }
}
