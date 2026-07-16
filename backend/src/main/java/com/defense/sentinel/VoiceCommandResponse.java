package com.defense.sentinel;

import java.util.List;

/** Executed voice order returned to the commander dashboard. */
public class VoiceCommandResponse {
  public String action;
  public String callSign;
  public double latitude;
  public double longitude;
  public List<double[]> waypoints;

  public VoiceCommandResponse(
      String action,
      String callSign,
      double latitude,
      double longitude,
      List<double[]> waypoints) {
    this.action = action;
    this.callSign = callSign;
    this.latitude = latitude;
    this.longitude = longitude;
    this.waypoints = waypoints;
  }
}
