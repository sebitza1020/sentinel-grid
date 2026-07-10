package com.defense.sentinel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.defense.sentinel.websocket.TelemetrySocket;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Plain unit test (no @QuarkusTest) for the fleet orchestration. Mocks the concrete TelemetrySocket
 * (Mockito is available via quarkus-junit5-mockito) and drives a hand-built fleet pool — mirroring
 * TelemetrySocketTest.
 */
class FleetCommanderServiceTest {

  private static Map<String, Object> unit(double lat, double lng, int battery, String threat) {
    Map<String, Object> s = new HashMap<>();
    s.put("lat", lat);
    s.put("lng", lng);
    s.put("battery", battery);
    if (threat != null) {
      s.put("threat_level", threat);
    }
    return s;
  }

  private FleetCommanderService withPool(Map<String, Map<String, Object>> pool, TelemetrySocket socket) {
    when(socket.currentStates()).thenReturn(pool);
    FleetCommanderService fc = new FleetCommanderService();
    fc.telemetrySocket = socket;
    return fc;
  }

  @Test
  void reroutesTheClosestAvailableUnit() {
    // Threat at Bucharest centre. NEAR is closest & healthy; FAR is healthy but farther;
    // LOW is close but out of battery; BUSY is close but already on a THREAT.
    Map<String, Map<String, Object>> pool = new HashMap<>();
    pool.put("ALPHA", unit(44.4268, 26.1025, 90, "THREAT")); // the threatened unit itself
    pool.put("NEAR", unit(44.4300, 26.1060, 80, "SAFE"));
    pool.put("FAR", unit(44.5000, 26.2000, 95, "SAFE"));
    pool.put("LOW", unit(44.4270, 26.1030, 10, "SAFE"));
    pool.put("BUSY", unit(44.4269, 26.1026, 70, "THREAT"));

    TelemetrySocket socket = mock(TelemetrySocket.class);
    FleetCommanderService fc = withPool(pool, socket);

    fc.reinforce("ALPHA", 44.4268, 26.1025);

    // Only NEAR qualifies as the closest available unit and is ordered directly to the threat.
    verify(socket).broadcastPath(eq("NEAR"), any(), eq(true));
    verify(socket).updateAndBroadcast(eq("NEAR"), any());
    // Excluded units are never re-tasked.
    verify(socket, never()).broadcastPath(eq("ALPHA"), any(), eq(true));
    verify(socket, never()).broadcastPath(eq("FAR"), any(), eq(true));
    verify(socket, never()).broadcastPath(eq("LOW"), any(), eq(true));
    verify(socket, never()).broadcastPath(eq("BUSY"), any(), eq(true));
  }

  @Test
  void doesNothingWhenNoUnitIsAvailable() {
    Map<String, Map<String, Object>> pool = new HashMap<>();
    pool.put("ALPHA", unit(44.4268, 26.1025, 90, "THREAT")); // itself
    pool.put("LOW", unit(44.4270, 26.1030, 5, "SAFE")); // out of battery
    pool.put("BUSY", unit(44.4269, 26.1026, 70, "THREAT")); // already engaged

    TelemetrySocket socket = mock(TelemetrySocket.class);
    FleetCommanderService fc = withPool(pool, socket);

    fc.reinforce("ALPHA", 44.4268, 26.1025);

    verify(socket, never()).broadcastPath(anyString(), any(), eq(true));
  }

  @Test
  void haversineMatchesKnownDistance() {
    // Bucharest → Cluj-Napoca is ~ 325 km; allow a generous tolerance.
    double d = FleetCommanderService.haversine(44.4268, 26.1025, 46.7712, 23.6236);
    assertTrue(d > 300_000 && d < 350_000, "expected ~325km, got " + d);
    assertEquals(0.0, FleetCommanderService.haversine(44.0, 26.0, 44.0, 26.0), 1e-6);
  }
}
