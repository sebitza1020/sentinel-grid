package com.defense.sentinel;

import com.defense.sentinel.client.OpenMeteoClient;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Path("/api/weather")
@Produces(MediaType.APPLICATION_JSON)
public class WeatherResource {

  // Coordonate hardcodate pentru București
  private static final double BUCHAREST_LAT = 44.4268;
  private static final double BUCHAREST_LNG = 26.1025;
  private static final String CURRENT_FIELDS = "temperature_2m,relative_humidity_2m,wind_speed_10m";

  @Inject
  @RestClient
  OpenMeteoClient openMeteoClient;

  @GET
  public WeatherDTO getBucharestWeather() {
    return openMeteoClient.getForecast(BUCHAREST_LAT, BUCHAREST_LNG, CURRENT_FIELDS);
  }
}
