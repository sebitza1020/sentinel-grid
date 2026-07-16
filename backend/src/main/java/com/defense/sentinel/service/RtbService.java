package com.defense.sentinel.service;

import com.defense.sentinel.WeatherDTO;
import com.defense.sentinel.websocket.TelemetrySocket;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Energy-decay engine + Autonomous Return-To-Base. Each telemetry tick, this drains a drone's battery
 * from elapsed time, flight speed, and live wind; when it falls below the critical threshold the drone
 * is autonomously re-tasked to the nearest defensive base, flagged {@code status="RTB"}, and the
 * override is broadcast so the UI shows the landing sequence.
 *
 * <p>The engine is authoritative over battery: the frontend simulation reports a placeholder value, and
 * this overrides it with the real decaying level before the state is broadcast.
 */
@ApplicationScoped
public class RtbService {

  /** A tactical recovery point. */
  public record BaseStation(String name, double lat, double lng) {}

  // Defensive base stations (hardcoded tactical coordinates).
  static final List<BaseStation> BASES =
      List.of(
          new BaseStation("Base Alpha", 44.4268, 26.1025), // Bucharest center
          new BaseStation("Base Beta", 44.5500, 26.0700)); // northern outskirts

  static final int RTB_THRESHOLD = 15; // battery % below which RTB engages
  static final double BASE_DRAIN = 4.0; // %/tick baseline
  static final double SPEED_FACTOR = 0.10; // %/tick per m/s of ground speed
  static final double WIND_FACTOR = 0.20; // %/tick per km/h of wind
  static final double LAND_RADIUS_M = 500.0; // "landed" when within this of the base
  static final double RECHARGE_PER_TICK = 20.0; // %/tick once safely landed
  static final double BATTERY_FLOOR = 3.0; // don't let a returning drone hit zero
  static final double MAX_ELAPSED_TICKS = 4.0; // clamp so a long pause can't nuke the battery
  private static final long TICK_MILLIS = 15_000L;
  private static final double EARTH_RADIUS_M = 6_371_000.0;

  @Inject TelemetrySocket telemetrySocket;

  @Inject WeatherService weatherService;

  @Inject NavigationService navigationService;

  // Starea energetică per dronă (id de callSign). Package-private pentru teste.
  final Map<String, FlightState> flightStates = new ConcurrentHashMap<>();

  /** Per-drone energy + RTB bookkeeping. */
  static class FlightState {
    double battery;
    double lat;
    double lng;
    long lastTickMs;
    String status; // ACTIVE, RTB, LANDED
    double baseLat;
    double baseLng;

    FlightState(double battery, double lat, double lng, long lastTickMs, String status) {
      this.battery = battery;
      this.lat = lat;
      this.lng = lng;
      this.lastTickMs = lastTickMs;
      this.status = status;
    }
  }

  /**
   * Advance one telemetry tick for {@code callSign}: decay/recharge the battery and, if critical,
   * engage Autonomous RTB. Mutates {@code update} (battery, and status/target when RTB or LANDED) so
   * the caller's broadcast carries the override.
   */
  public void apply(String callSign, double lat, double lng, Map<String, Object> update) {
    long now = System.currentTimeMillis();
    FlightState state = flightStates.get(callSign);

    if (state == null) {
      // Prima observare: pornim cu baterie plină, fără decădere.
      state = new FlightState(100.0, lat, lng, now, "ACTIVE");
      flightStates.put(callSign, state);
      writeBattery(update, state.battery);
      return;
    }

    double elapsedTicks = clamp((now - state.lastTickMs) / (double) TICK_MILLIS, 0.1, MAX_ELAPSED_TICKS);
    double elapsedSec = Math.max(elapsedTicks * (TICK_MILLIS / 1000.0), 1.0);
    double speed = haversine(state.lat, state.lng, lat, lng) / elapsedSec; // m/s
    double wind = currentWindKmh();

    if ("LANDED".equals(state.status)) {
      state.battery = Math.min(100.0, state.battery + RECHARGE_PER_TICK * elapsedTicks);
    } else {
      double drain = computeDrain(elapsedTicks, speed, wind);
      state.battery = Math.max(BATTERY_FLOOR, state.battery - drain);
    }

    // Declanșare RTB: baterie critică, dronă încă activă.
    if (!"RTB".equals(state.status)
        && !"LANDED".equals(state.status)
        && state.battery < RTB_THRESHOLD) {
      double altitude = asDouble(update.get("alt"), 0.0);
      BaseStation base = engageRtb(callSign, lat, lng, altitude);
      if (base != null) {
        state.status = "RTB";
        state.baseLat = base.lat();
        state.baseLng = base.lng();
      }
    }

    // Aterizare: am ajuns suficient de aproape de baza asignată.
    if ("RTB".equals(state.status)
        && haversine(lat, lng, state.baseLat, state.baseLng) < LAND_RADIUS_M) {
      state.status = "LANDED";
      System.out.println("[SYSTEM] " + callSign + " landed safely. Recovery in progress.");
    }

    // Scriem starea în update-ul care va fi difuzat.
    writeBattery(update, state.battery);
    if ("RTB".equals(state.status) || "LANDED".equals(state.status)) {
      update.put("status", state.status);
    }
    if ("RTB".equals(state.status)) {
      update.put("target_lat", state.baseLat);
      update.put("target_lng", state.baseLng);
      update.put("target_alt", 0.0);
    }

    state.lat = lat;
    state.lng = lng;
    state.lastTickMs = now;
  }

  private double currentWindKmh() {
    WeatherDTO weather = weatherService.getBucharestWeather();
    if (weather != null && weather.current != null) {
      return weather.current.wind_speed_10m;
    }
    return 0.0;
  }

  private BaseStation engageRtb(
      String callSign, double lat, double lng, double altitude) {
    List<BaseStation> candidates = new ArrayList<>(BASES);
    candidates.sort(
        Comparator.comparingDouble(base -> haversine(lat, lng, base.lat(), base.lng())));

    for (BaseStation base : candidates) {
      try {
        List<double[]> route =
            navigationService.route(
                new double[] {lat, lng, Math.max(0.0, altitude)},
                new double[] {base.lat(), base.lng(), 0.0});
        System.out.println(
            "[SYSTEM] Critical battery level on "
                + callSign
                + ". Initiating Autonomous RTB protocol to "
                + base.name()
                + "!");
        telemetrySocket.broadcastPath(callSign, route, false, true);
        return base;
      } catch (RouteBlockedException e) {
        System.out.println(
            "[SYSTEM] "
                + base.name()
                + " is unreachable for "
                + callSign
                + "; trying the next recovery point.");
      }
    }

    System.err.println(
        "[SYSTEM] No safe RTB route is available for "
            + callSign
            + "; holding current position.");
    return null;
  }

  private static void writeBattery(Map<String, Object> update, double battery) {
    int rounded = (int) Math.round(battery);
    update.put("battery", rounded);
    update.put("batt", rounded);
  }

  private static double asDouble(Object value, double fallback) {
    return value instanceof Number ? ((Number) value).doubleValue() : fallback;
  }

  // --- Pure, testable helpers ---

  static double computeDrain(double elapsedTicks, double speedMps, double windKmh) {
    return (BASE_DRAIN + SPEED_FACTOR * speedMps + WIND_FACTOR * windKmh) * elapsedTicks;
  }

  static BaseStation closestBase(double lat, double lng) {
    BaseStation best = BASES.get(0);
    double bestDist = Double.MAX_VALUE;
    for (BaseStation base : BASES) {
      double dist = haversine(lat, lng, base.lat(), base.lng());
      if (dist < bestDist) {
        bestDist = dist;
        best = base;
      }
    }
    return best;
  }

  /** Great-circle distance in metres between two lat/lng points. */
  static double haversine(double lat1, double lng1, double lat2, double lng2) {
    double dLat = Math.toRadians(lat2 - lat1);
    double dLng = Math.toRadians(lng2 - lng1);
    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2)
                * Math.sin(dLng / 2);
    return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }
}
