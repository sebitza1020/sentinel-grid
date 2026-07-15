package com.defense.sentinel;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Arrays;

public class DroneCreateRequest {
  @NotBlank
  @Size(max = 32)
  @Pattern(regexp = "[A-Za-z0-9-]+", message = "must contain only letters, numbers, and hyphens")
  public String callSign;

  @NotBlank
  @Size(max = 80)
  public String model;

  @NotNull public DroneClass modelClass;

  @NotNull @Positive public Double topSpeed;

  @NotNull @Positive public Double radarRange;

  @NotNull @Positive public Double payloadCapacity;

  @NotNull @Positive public Integer batteryCapacity;

  @NotNull @Size(min = 1, max = 8) public String[] visionModes;

  @AssertTrue(message = "visionModes must contain only non-blank values")
  public boolean isVisionModesValid() {
    return visionModes == null || Arrays.stream(visionModes).allMatch(mode -> mode != null && !mode.isBlank());
  }
}
