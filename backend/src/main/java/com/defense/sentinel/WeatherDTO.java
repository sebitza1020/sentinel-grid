package com.defense.sentinel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// Mapează răspunsul JSON de la Open-Meteo. Ignorăm câmpurile extra
// (latitude, generationtime_ms, current_units, etc.) de care nu avem nevoie.
@JsonIgnoreProperties(ignoreUnknown = true)
public class WeatherDTO {

  @JsonProperty("current")
  public Current current;

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Current {

    @JsonProperty("temperature_2m")
    public double temperature_2m;

    @JsonProperty("wind_speed_10m")
    public double wind_speed_10m;

    @JsonProperty("relative_humidity_2m")
    public int relative_humidity_2m;
  }
}
