package com.defense.sentinel;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Mockito.when;

import com.defense.sentinel.service.WeatherService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Baseline {@code @QuarkusTest} for the {@code /api/weather} endpoint. The {@link WeatherService}
 * is mocked so the test never touches Open-Meteo (or the network), and the {@code %test} profile
 * keeps the datasource/Hibernate/Firebase inactive so the app boots without external services.
 */
@QuarkusTest
class WeatherResourceTest {

  @InjectMock WeatherService weatherService;

  @Test
  void returnsCachedWeatherPayload() {
    WeatherDTO dto = new WeatherDTO();
    dto.current = new WeatherDTO.Current();
    dto.current.temperature_2m = 27.3;
    dto.current.wind_speed_10m = 4.7;
    dto.current.relative_humidity_2m = 38;

    when(weatherService.getBucharestWeather()).thenReturn(dto);

    given()
        .when()
        .get("/api/weather")
        .then()
        .statusCode(200)
        .body("current.temperature_2m", is(27.3f))
        .body("current.relative_humidity_2m", is(38));
  }
}
