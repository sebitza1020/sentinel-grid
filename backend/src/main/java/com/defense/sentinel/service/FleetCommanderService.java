package com.defense.sentinel.service;

import com.defense.sentinel.websocket.TelemetrySocket;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Autonomous fleet orchestration. When a drone reports a strict THREAT, the commander scans the live
 * fleet pool, picks the closest unit that is available (not already engaged, battery OK), and re-routes
 * it directly to the threat coordinates as tactical reinforcement — broadcasting the order so every
 * dashboard animates the reinforcement in real time.
 */
@ApplicationScoped
public class FleetCommanderService {

  // Package-private so a plain-JUnit test can inject a mocked TelemetrySocket.
  @Inject TelemetrySocket telemetrySocket;

  @Inject NavigationService navigationService;

  // Units below this battery percentage are not eligible to be re-tasked.
  static final int MIN_BATTERY = 25;

  private static final double EARTH_RADIUS_M = 6_371_000.0;

  /**
   * Re-task the closest available drone to reinforce a threat at {@code (threatLat, threatLng)}.
   *
   * @param threatCallSign the drone that raised the THREAT (excluded from reinforcement candidates)
   */
  public void reinforce(String threatCallSign, double threatLat, double threatLng) {
    reinforce(threatCallSign, threatLat, threatLng, 0.0);
  }

  /**
   * Re-task the closest available drone whose three-dimensional path to the threat can be planned
   * safely.
   */
  public void reinforce(
      String threatCallSign, double threatLat, double threatLng, double threatAltitude) {
    Map<String, Map<String, Object>> pool = telemetrySocket.currentStates();
    List<Candidate> candidates = new ArrayList<>();

    for (Map.Entry<String, Map<String, Object>> entry : pool.entrySet()) {
      String callSign = entry.getKey();
      Map<String, Object> state = entry.getValue();

      if (callSign.equals(threatCallSign)) {
        continue; // don't send the threatened unit to reinforce itself
      }
      if ("THREAT".equals(state.get("threat_level"))) {
        continue; // already handling a threat
      }
      Integer battery = asInt(state.get("battery"));
      if (battery == null || battery < MIN_BATTERY) {
        continue; // too low on power to be re-tasked
      }
      Double lat = asDouble(state.get("lat"));
      Double lng = asDouble(state.get("lng"));
      if (lat == null || lng == null) {
        continue; // no known position
      }

      double distance = haversine(threatLat, threatLng, lat, lng);
      Double altitude = asDouble(state.get("alt"));
      candidates.add(
          new Candidate(
              callSign, lat, lng, altitude == null ? 0.0 : Math.max(0.0, altitude), distance));
    }

    candidates.sort(Comparator.comparingDouble(Candidate::distance));
    for (Candidate candidate : candidates) {
      try {
        List<double[]> route =
            navigationService.route(
                new double[] {candidate.lat(), candidate.lng(), candidate.altitude()},
                new double[] {threatLat, threatLng, Math.max(0.0, threatAltitude)});
        dispatch(candidate.callSign(), threatLat, threatLng, threatAltitude, route);
        return;
      } catch (RouteBlockedException e) {
        System.out.println(
            "[COMMANDER] Skipping "
                + candidate.callSign()
                + ": restricted airspace blocks reinforcement route.");
      }
    }

    System.out.println("[COMMANDER] No available unit can safely reinforce " + threatCallSign + ".");
  }

  private void dispatch(
      String callSign,
      double threatLat,
      double threatLng,
      double threatAltitude,
      List<double[]> route) {
    System.out.println(
        "[COMMANDER] Re-routing "
            + callSign
            + " to reinforce location due to THREAT level alert!");

    telemetrySocket.broadcastPath(callSign, route, true);

    // Publish + broadcast the reinforcer's new tactical state (the snapshot covers both drones).
    Map<String, Object> reinforcement = new HashMap<>();
    reinforcement.put("status", "REINFORCING");
    reinforcement.put("target_lat", threatLat);
    reinforcement.put("target_lng", threatLng);
    reinforcement.put("target_alt", Math.max(0.0, threatAltitude));
    telemetrySocket.updateAndBroadcast(callSign, reinforcement);
  }

  private record Candidate(
      String callSign, double lat, double lng, double altitude, double distance) {}

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

  private static Integer asInt(Object value) {
    return value instanceof Number ? ((Number) value).intValue() : null;
  }

  private static Double asDouble(Object value) {
    return value instanceof Number ? ((Number) value).doubleValue() : null;
  }
}
