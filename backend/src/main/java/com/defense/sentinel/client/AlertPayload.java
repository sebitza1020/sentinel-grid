package com.defense.sentinel.client;

// Definim ce trimitem la Google
public class AlertPayload {
  public String callSign;
  public String report;
  public double lat;
  public double lng;

  public AlertPayload(String callSign, String report, double lat, double lng) {
    this.callSign = callSign;
    this.report = report;
    this.lat = lat;
    this.lng = lng;
  }
}
