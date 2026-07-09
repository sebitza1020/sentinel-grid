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

  // Package-private for plain-JUnit tests (mirrors WeatherService's testable-field convention).
  final List<Geofence> zones = new CopyOnWriteArrayList<>();

  /** Replace the whole active set (the frontend syncs the full featureGroup on each change). */
  public void replaceAll(List<Geofence> next) {
    zones.clear();
    if (next != null) {
      zones.addAll(next);
    }
  }

  public List<Geofence> getZones() {
    return new ArrayList<>(zones);
  }

  public void clear() {
    zones.clear();
  }
}
