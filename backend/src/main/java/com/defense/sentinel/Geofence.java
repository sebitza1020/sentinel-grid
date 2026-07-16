package com.defense.sentinel;

/**
 * A volumetric No-Fly Zone. The polygon is the horizontal footprint and the altitude bounds are metres
 * above ground level (AGL). Flat public fields mirror the codebase's DTO style.
 */
public class Geofence {
  public String id;
  public double[][] polygon;
  public Double minAltitude;
  public Double maxAltitude;

  public Geofence() {}

  public Geofence(String id, double[][] polygon) {
    this(id, polygon, 0.0, 500.0);
  }

  public Geofence(
      String id, double[][] polygon, double minAltitude, double maxAltitude) {
    this.id = id;
    this.polygon = polygon;
    this.minAltitude = minAltitude;
    this.maxAltitude = maxAltitude;
  }
}
