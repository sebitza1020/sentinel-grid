package com.defense.sentinel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.defense.sentinel.VoiceCommandIntent;
import com.defense.sentinel.VoiceCommandResponse;
import com.defense.sentinel.websocket.TelemetrySocket;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VoiceFleetCommandServiceTest {

  private VoiceFleetCommandService service(
      TelemetrySocket socket, NavigationService navigationService) {
    VoiceFleetCommandService service = new VoiceFleetCommandService();
    service.telemetrySocket = socket;
    service.navigationService = navigationService;
    return service;
  }

  @Test
  void plansAndBroadcastsSafeMoveFromLivePosition() {
    TelemetrySocket socket = mock(TelemetrySocket.class);
    NavigationService navigation = mock(NavigationService.class);
    when(socket.currentStates())
        .thenReturn(
            Map.of(
                "RAZOR-12",
                Map.of("lat", 44.4, "lng", 26.1, "alt", 175.0, "status", "ACTIVE")));
    List<double[]> route = List.of(new double[] {44.4268, 26.1042, 175.0});
    when(navigation.route(any(), any())).thenReturn(route);

    VoiceCommandResponse response =
        service(socket, navigation)
            .execute(new VoiceCommandIntent("MOVE", "razor-12", 44.4268, 26.1042));

    assertEquals("RAZOR-12", response.callSign);
    assertEquals(route, response.waypoints);
    verify(socket).broadcastPath("RAZOR-12", route);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> state =
        ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
    verify(socket).updateAndBroadcast(eq("RAZOR-12"), state.capture());
    assertEquals("EN_ROUTE", state.getValue().get("status"));
    assertEquals(44.4268, state.getValue().get("target_lat"));
    assertEquals(26.1042, state.getValue().get("target_lng"));
    assertEquals(175.0, state.getValue().get("target_alt"));
  }

  @Test
  void rejectsMissingDronePositionAndAutonomousOverrides() {
    TelemetrySocket socket = mock(TelemetrySocket.class);
    NavigationService navigation = mock(NavigationService.class);
    VoiceFleetCommandService service = service(socket, navigation);
    VoiceCommandIntent intent = new VoiceCommandIntent("MOVE", "RAZOR-12", 44.5, 26.2);

    when(socket.currentStates()).thenReturn(Map.of());
    assertExecutionKind(
        VoiceCommandExecutionException.Kind.DRONE_NOT_FOUND, () -> service.execute(intent));

    when(socket.currentStates()).thenReturn(Map.of("RAZOR-12", Map.of("status", "ACTIVE")));
    assertExecutionCode("POSITION_UNAVAILABLE", () -> service.execute(intent));

    when(socket.currentStates())
        .thenReturn(
            Map.of("RAZOR-12", Map.of("status", "ACTIVE", "lat", 100.0, "lng", 26.1)));
    assertExecutionCode("POSITION_UNAVAILABLE", () -> service.execute(intent));

    when(socket.currentStates())
        .thenReturn(Map.of("RAZOR-12", Map.of("status", "RTB", "lat", 44.4, "lng", 26.1)));
    assertExecutionCode("AUTONOMOUS_OVERRIDE", () -> service.execute(intent));

    when(socket.currentStates())
        .thenReturn(Map.of("RAZOR-12", Map.of("status", "LANDED", "lat", 44.4, "lng", 26.1)));
    assertExecutionCode("AUTONOMOUS_OVERRIDE", () -> service.execute(intent));
  }

  @Test
  void reportsBlockedRoutesAsConflicts() {
    TelemetrySocket socket = mock(TelemetrySocket.class);
    NavigationService navigation = mock(NavigationService.class);
    when(socket.currentStates())
        .thenReturn(Map.of("RAZOR-12", Map.of("lat", 44.4, "lng", 26.1, "alt", 100)));
    when(navigation.route(any(), any())).thenThrow(new RouteBlockedException("blocked"));

    assertExecutionCode(
        "ROUTE_BLOCKED",
        () ->
            service(socket, navigation)
                .execute(new VoiceCommandIntent("MOVE", "RAZOR-12", 44.5, 26.2)));
  }

  private static void assertExecutionKind(
      VoiceCommandExecutionException.Kind kind, Runnable invocation) {
    VoiceCommandExecutionException error =
        assertThrows(VoiceCommandExecutionException.class, invocation::run);
    assertEquals(kind, error.kind());
  }

  private static void assertExecutionCode(String code, Runnable invocation) {
    VoiceCommandExecutionException error =
        assertThrows(VoiceCommandExecutionException.class, invocation::run);
    assertEquals(code, error.code());
  }
}
