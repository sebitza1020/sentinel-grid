package com.defense.sentinel.client;

import com.defense.sentinel.WeatherDTO;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

// Clientul REST care trage datele meteo de la Open-Meteo (fără API key)
@RegisterRestClient(baseUri = "https://api.open-meteo.com/v1")
public interface OpenMeteoClient {

  @GET
  @Path("/forecast")
  @Produces(MediaType.APPLICATION_JSON)
  WeatherDTO getForecast(
      @QueryParam("latitude") double latitude,
      @QueryParam("longitude") double longitude,
      @QueryParam("current") String current);
}
