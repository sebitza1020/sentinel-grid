package com.defense.sentinel;

/** Validated AI interpretation of a commander voice order. */
public class VoiceCommandIntent {
  public String action;
  public String callSign;
  public double latitude;
  public double longitude;

  public VoiceCommandIntent() {}

  public VoiceCommandIntent(String action, String callSign, double latitude, double longitude) {
    this.action = action;
    this.callSign = callSign;
    this.latitude = latitude;
    this.longitude = longitude;
  }
}
