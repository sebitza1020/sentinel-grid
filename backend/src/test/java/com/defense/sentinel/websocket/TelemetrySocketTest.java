package com.defense.sentinel.websocket;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Plain unit test (no @QuarkusTest) for the telemetry broadcast. Uses a mocked {@link Session} so no
 * server/boot is needed — mirroring {@code WeatherServiceTest}. Verifies fan-out and the merge
 * semantics that keep a prior {@code threat_level} across movement-only updates.
 */
class TelemetrySocketTest {

  private TelemetrySocket newSocket(Session session) {
    TelemetrySocket socket = new TelemetrySocket();
    socket.objectMapper = new ObjectMapper();
    socket.sessions.add(session);
    return socket;
  }

  @Test
  void broadcastsUpdatesToOpenSessions() {
    RemoteEndpoint.Async async = mock(RemoteEndpoint.Async.class);
    Session session = mock(Session.class);
    when(session.isOpen()).thenReturn(true);
    when(session.getAsyncRemote()).thenReturn(async);

    TelemetrySocket socket = newSocket(session);
    socket.updateAndBroadcast("FALCON-1", Map.of("lat", 44.4, "threat_level", "THREAT"));

    // The whole fleet snapshot, keyed by call sign, reaches the client.
    verify(async).sendText(contains("FALCON-1"));
    verify(async).sendText(contains("THREAT"));
  }

  @Test
  void mergesStateSoMovementDoesNotWipeThreatLevel() {
    Session session = mock(Session.class);
    when(session.isOpen()).thenReturn(true);
    when(session.getAsyncRemote()).thenReturn(mock(RemoteEndpoint.Async.class));

    TelemetrySocket socket = newSocket(session);
    socket.updateAndBroadcast("FALCON-1", Map.of("lat", 44.4, "threat_level", "THREAT"));
    // A movement-only ping (no threat_level) must not erase the earlier verdict.
    socket.updateAndBroadcast("FALCON-1", Map.of("lat", 44.5, "lng", 26.1));

    String snapshot = socket.snapshotJson();
    assertNotNull(snapshot);
    assertTrue(snapshot.contains("THREAT"), "threat_level should survive a movement-only update");
    assertTrue(snapshot.contains("44.5"), "latest position should be reflected");
  }
}
