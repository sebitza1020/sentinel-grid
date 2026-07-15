package com.defense.sentinel;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Arrays;

public class DroneUpdateRequest {
  @Size(min = 1, max = 32)
  @Pattern(regexp = "[A-Za-z0-9-]+", message = "must contain only letters, numbers, and hyphens")
  public String callSign;

  @Size(min = 1, max = 80) public String model;

  @Size(min = 1, max = 32) public String status;

  public DroneClass modelClass;

  @Positive public Double topSpeed;

  @Positive public Double radarRange;

  @Positive public Double payloadCapacity;

  @Positive public Integer batteryCapacity;

  @Size(min = 1, max = 8) public String[] visionModes;

  @AssertTrue(message = "visionModes must contain only non-blank values")
  public boolean isVisionModesValid() {
    return visionModes == null || Arrays.stream(visionModes).allMatch(mode -> mode != null && !mode.isBlank());
  }

  @AssertTrue(message = "model and status must not be blank")
  public boolean areOptionalStringsValid() {
    return (model == null || !model.isBlank()) && (status == null || !status.isBlank());
  }
}
