package com.defense.sentinel;

import com.defense.sentinel.service.WeatherService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/weather")
@Produces(MediaType.APPLICATION_JSON)
public class WeatherResource {

  @Inject
  WeatherService weatherService;

  @GET
  public WeatherDTO getBucharestWeather() {
    // Cache-ul + fallback-ul stale sunt gestionate în WeatherService
    return weatherService.getBucharestWeather();
  }
}
