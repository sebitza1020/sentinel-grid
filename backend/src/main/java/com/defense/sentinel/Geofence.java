package com.defense.sentinel;

/**
 * A No-Fly Zone: a closed polygon of geographic vertices. Flat public fields to mirror the codebase's
 * DTO style ({@code TelemetryData}, {@code WeatherDTO}). Each row of {@code polygon} is {@code [lat, lng]}.
 */
public class Geofence {
  public String id;
  public double[][] polygon;

  public Geofence() {}

  public Geofence(String id, double[][] polygon) {
    this.id = id;
    this.polygon = polygon;
  }
}
