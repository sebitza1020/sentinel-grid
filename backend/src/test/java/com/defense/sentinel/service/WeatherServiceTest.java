package com.defense.sentinel.service;

import com.defense.sentinel.WeatherDTO;
import com.defense.sentinel.client.OpenMeteoClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Plain unit test (no @QuarkusTest) for the in-memory weather cache. Exercises
 * the three behaviors that neutralize the Open-Meteo 429: cache hits within the
 * TTL, stale-on-error fallback, and a null cold-start failure.
 */
class WeatherServiceTest {

  /** Hand-rolled fake so we don't need Mockito or a running Quarkus context. */
  static class FakeOpenMeteoClient implements OpenMeteoClient {
    int calls = 0;
    boolean fail = false;
    WeatherDTO next;

    @Override
    public WeatherDTO getForecast(double latitude, double longitude, String current) {
      calls++;
      if (fail) {
        throw new RuntimeException("Too Many Requests, status code 429");
      }
      return next;
    }
  }

  private WeatherService newService(FakeOpenMeteoClient client, long ttlMillis) {
    WeatherService service = new WeatherService();
    service.openMeteoClient = client; // package-private field injection point
    service.ttlMillis = ttlMillis;
    return service;
  }

  @Test
  void servesCachedValueWithinTtl() {
    FakeOpenMeteoClient client = new FakeOpenMeteoClient();
    client.next = new WeatherDTO();
    WeatherService service = newService(client, 60_000);

    WeatherDTO first = service.getBucharestWeather();
    WeatherDTO second = service.getBucharestWeather();

    assertSame(first, second, "second call should return the cached instance");
    assertEquals(1, client.calls, "upstream should be hit only once within the TTL");
  }

  @Test
  void servesStaleValueWhenRefreshFails() {
    FakeOpenMeteoClient client = new FakeOpenMeteoClient();
    client.next = new WeatherDTO();
    // ttl = 0 forces a refetch on every call
    WeatherService service = newService(client, 0);

    WeatherDTO good = service.getBucharestWeather(); // populates cache (calls == 1)
    client.fail = true;                              // upstream now 429s
    WeatherDTO stale = service.getBucharestWeather();

    assertSame(good, stale, "should fall back to the last good value, not throw");
    assertEquals(2, client.calls, "the failed refresh should still have been attempted");
  }

  @Test
  void returnsNullOnColdStartFailure() {
    FakeOpenMeteoClient client = new FakeOpenMeteoClient();
    client.fail = true;
    WeatherService service = newService(client, 60_000);

    assertNull(service.getBucharestWeather(), "no cache + failure yields null (204), not a 500");
  }
}
