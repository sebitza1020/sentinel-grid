package com.defense.sentinel.service;

import com.defense.sentinel.Geofence;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory registry of active No-Fly Zones. Kept in memory (not the DB) so it needs no datasource —
 * consistent with the ephemeral telemetry state and works under the {@code %test} profile. Thread-safe:
 * the navigation service reads {@link #getZones()} on request threads while the operator replaces the
 * set from HTTP threads.
 */
@ApplicationScoped
public class GeofenceService {

  public static final double DEFAULT_MIN_ALTITUDE = 0.0;
  public static final double DEFAULT_MAX_ALTITUDE = 500.0;

  // Package-private for plain-JUnit tests (mirrors WeatherService's testable-field convention).
  final List<Geofence> zones = new CopyOnWriteArrayList<>();

  /** Replace the whole active set (the frontend syncs the full featureGroup on each change). */
  public void replaceAll(List<Geofence> next) {
    List<Geofence> validated = new ArrayList<>();
    if (next != null) {
      for (Geofence zone : next) {
        validated.add(validateAndNormalize(zone));
      }
    }
    zones.clear();
    zones.addAll(validated);
  }

  public List<Geofence> getZones() {
    return new ArrayList<>(zones);
  }

  public void clear() {
    zones.clear();
  }

  private static Geofence validateAndNormalize(Geofence zone) {
    if (zone == null || zone.id == null || zone.id.isBlank()) {
      throw new IllegalArgumentException("Every geofence requires a non-empty id.");
    }
    if (zone.polygon == null || zone.polygon.length < 3) {
      throw new IllegalArgumentException("Geofence polygon requires at least three vertices.");
    }
    for (double[] vertex : zone.polygon) {
      if (vertex == null
          || vertex.length != 2
          || !Double.isFinite(vertex[0])
          || !Double.isFinite(vertex[1])
          || vertex[0] < -90
          || vertex[0] > 90
          || vertex[1] < -180
          || vertex[1] > 180) {
        throw new IllegalArgumentException(
            "Geofence vertices must be finite [lat, lng] coordinates.");
      }
    }

    double min =
        zone.minAltitude == null ? DEFAULT_MIN_ALTITUDE : zone.minAltitude;
    double max =
        zone.maxAltitude == null ? DEFAULT_MAX_ALTITUDE : zone.maxAltitude;
    if (!Double.isFinite(min) || !Double.isFinite(max) || min < 0 || max <= min) {
      throw new IllegalArgumentException(
          "Geofence altitude bounds must be finite, non-negative, and maxAltitude > minAltitude.");
    }

    return new Geofence(zone.id, zone.polygon, min, max);
  }
}
