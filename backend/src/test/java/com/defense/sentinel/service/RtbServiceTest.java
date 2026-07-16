package com.defense.sentinel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.defense.sentinel.WeatherDTO;
import com.defense.sentinel.websocket.TelemetrySocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Plain unit test (no @QuarkusTest) for the energy-decay + Autonomous RTB engine. Seeds per-drone
 * state directly to control battery/time (mirrors FleetCommanderServiceTest's Mockito approach).
 */
class RtbServiceTest {

  private RtbService newService(
      TelemetrySocket socket, NavigationService navigationService, double windKmh) {
    WeatherDTO weather = new WeatherDTO();
    weather.current = new WeatherDTO.Current();
    weather.current.wind_speed_10m = windKmh;
    WeatherService weatherService = mock(WeatherService.class);
    when(weatherService.getBucharestWeather()).thenReturn(weather);

    RtbService service = new RtbService();
    service.telemetrySocket = socket;
    service.weatherService = weatherService;
    service.navigationService = navigationService;
    return service;
  }

  private Map<String, Object> tick(double lat, double lng) {
    Map<String, Object> update = new HashMap<>();
    update.put("lat", lat);
    update.put("lng", lng);
    update.put("alt", 180.0);
    update.put("battery", 50); // frontend placeholder — must be overridden
    return update;
  }

  @Test
  void firstSightSeedsFullBatteryAndNoRtb() {
    TelemetrySocket socket = mock(TelemetrySocket.class);
    RtbService service = newService(socket, mock(NavigationService.class), 5);

    Map<String, Object> update = tick(44.43, 26.10);
    service.apply("FALCON-1", 44.43, 26.10, update);

    assertEquals(100, update.get("battery"), "first tick seeds a full battery");
    assertNull(update.get("status"), "no status flag for a healthy drone");
    verify(socket, never()).broadcastPath(eq("FALCON-1"), any(), eq(false), eq(true));
  }

  @Test
  void engagesRtbToClosestBaseWhenBatteryCritical() {
    TelemetrySocket socket = mock(TelemetrySocket.class);
    NavigationService navigation = mock(NavigationService.class);
    when(navigation.route(any(), any()))
        .thenReturn(List.of(new double[] {44.5500, 26.0700, 0}));
    RtbService service = newService(socket, navigation, 5);

    // Seed a drone one tick ago at 16% near the northern outskirts (closest to Base Beta).
    service.flightStates.put(
        "GHOST-2",
        new RtbService.FlightState(16.0, 44.54, 26.07, System.currentTimeMillis() - 15_000, "ACTIVE"));

    Map<String, Object> update = tick(44.545, 26.072);
    service.apply("GHOST-2", 44.545, 26.072, update);

    assertTrue((int) update.get("battery") < RtbService.RTB_THRESHOLD, "battery decayed under threshold");
    assertEquals("RTB", update.get("status"), "status flips to RTB");
    assertEquals(44.5500, update.get("target_lat"), "routed to Base Beta (closest)");
    assertEquals(26.0700, update.get("target_lng"));
    verify(socket).broadcastPath(eq("GHOST-2"), any(), eq(false), eq(true));
  }

  @Test
  void healthyDroneKeepsFlyingWithoutRtb() {
    TelemetrySocket socket = mock(TelemetrySocket.class);
    RtbService service = newService(socket, mock(NavigationService.class), 5);
    service.flightStates.put(
        "REAPER-7",
        new RtbService.FlightState(80.0, 44.43, 26.10, System.currentTimeMillis() - 15_000, "ACTIVE"));

    Map<String, Object> update = tick(44.432, 26.102);
    service.apply("REAPER-7", 44.432, 26.102, update);

    assertTrue((int) update.get("battery") < 80, "battery decays each tick");
    assertNull(update.get("status"), "still ACTIVE — no RTB, status untouched");
    verify(socket, never()).broadcastPath(eq("REAPER-7"), any(), eq(false), eq(true));
  }

  @Test
  void closestBaseAndDrainMathAreCorrect() {
    // A point deep in Bucharest center is closest to Base Alpha; the far north to Base Beta.
    assertEquals("Base Alpha", RtbService.closestBase(44.42, 26.10).name());
    assertEquals("Base Beta", RtbService.closestBase(44.56, 26.06).name());

    double still = RtbService.computeDrain(1.0, 0, 0);
    double windy = RtbService.computeDrain(1.0, 0, 20);
    double fast = RtbService.computeDrain(1.0, 30, 0);
    assertTrue(windy > still, "wind increases drain");
    assertTrue(fast > still, "speed increases drain");

    assertEquals(0.0, RtbService.haversine(44.4, 26.1, 44.4, 26.1), 1e-6);
  }

  @Test
  void triesTheNextBaseAndHoldsWhenNoSafeRtbRouteExists() {
    TelemetrySocket socket = mock(TelemetrySocket.class);
    NavigationService fallbackNavigation = mock(NavigationService.class);
    when(fallbackNavigation.route(any(), any()))
        .thenThrow(new RouteBlockedException("blocked"))
        .thenReturn(List.of(new double[] {44.5500, 26.0700, 0}));
    RtbService fallbackService = newService(socket, fallbackNavigation, 0);
    fallbackService.flightStates.put(
        "FALLBACK",
        new RtbService.FlightState(
            16.0, 44.43, 26.10, System.currentTimeMillis() - 15_000, "ACTIVE"));

    Map<String, Object> fallbackUpdate = tick(44.431, 26.101);
    fallbackService.apply("FALLBACK", 44.431, 26.101, fallbackUpdate);

    assertEquals("RTB", fallbackUpdate.get("status"));
    verify(socket).broadcastPath(eq("FALLBACK"), any(), eq(false), eq(true));

    TelemetrySocket blockedSocket = mock(TelemetrySocket.class);
    NavigationService blockedNavigation = mock(NavigationService.class);
    when(blockedNavigation.route(any(), any()))
        .thenThrow(new RouteBlockedException("blocked"));
    RtbService blockedService = newService(blockedSocket, blockedNavigation, 0);
    blockedService.flightStates.put(
        "BLOCKED",
        new RtbService.FlightState(
            16.0, 44.43, 26.10, System.currentTimeMillis() - 15_000, "ACTIVE"));

    Map<String, Object> blockedUpdate = tick(44.431, 26.101);
    blockedService.apply("BLOCKED", 44.431, 26.101, blockedUpdate);

    assertNull(blockedUpdate.get("status"));
    verify(blockedSocket, never()).broadcastPath(eq("BLOCKED"), any(), eq(false), eq(true));
  }
}
