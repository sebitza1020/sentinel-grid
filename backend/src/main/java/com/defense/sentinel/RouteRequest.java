package com.defense.sentinel;

/**
 * A navigation request: plan a safe path for {@code callSign} from {@code start} to {@code end}.
 * Coordinates are {@code [lat, lng, altitudeAgl]}; legacy two-value coordinates are accepted as
 * altitude zero. Flat public fields mirror the codebase DTO style.
 */
public class RouteRequest {
  public String callSign;
  public double[] start;
  public double[] end;
}
