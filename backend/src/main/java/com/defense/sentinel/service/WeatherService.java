package com.defense.sentinel.service;

import com.defense.sentinel.WeatherDTO;
import com.defense.sentinel.client.OpenMeteoClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Cache in-memory pentru datele meteo. Open-Meteo limitează cererile per-IP
 * (HTTP 429), iar pe Render IP-ul de egress e partajat, așa că nu putem lovi
 * upstream la fiecare request. Ținem ultimul rezultat pentru un interval (TTL)
 * și, dacă refresh-ul eșuează, servim valoarea veche (stale) în loc de eroare.
 */
@ApplicationScoped
public class WeatherService {

  // Coordonate hardcodate pentru București
  private static final double BUCHAREST_LAT = 44.4268;
  private static final double BUCHAREST_LNG = 26.1025;
  private static final String CURRENT_FIELDS = "temperature_2m,relative_humidity_2m,wind_speed_10m";

  @Inject
  @RestClient
  OpenMeteoClient openMeteoClient;

  // Cât timp reutilizăm un rezultat înainte de a reinteroga Open-Meteo (implicit 10 min)
  @ConfigProperty(name = "weather.cache.ttl-millis", defaultValue = "600000")
  long ttlMillis;

  private volatile WeatherDTO cached;
  private volatile long lastFetch;

  public WeatherDTO getBucharestWeather() {
    // Cache hit rapid, fără lock
    if (isFresh()) {
      return cached;
    }

    // Cache expirat/gol: reîmprospătăm o singură dată (evităm stampede-ul)
    synchronized (this) {
      // Double-check: alt thread poate să fi reîmprospătat deja cât am așteptat
      if (isFresh()) {
        return cached;
      }

      try {
        System.out.println("🌡️ Fetching fresh weather from Open-Meteo...");
        WeatherDTO fresh = openMeteoClient.getForecast(BUCHAREST_LAT, BUCHAREST_LNG, CURRENT_FIELDS);
        cached = fresh;
        lastFetch = System.currentTimeMillis();
        return fresh;
      } catch (Exception e) {
        // 429 / timeout / outage: servim ultima valoare bună dacă o avem
        System.err.println("⚠️ Open-Meteo fetch failed (" + e.getMessage()
            + "). Serving " + (cached != null ? "cached" : "no") + " data.");
        return cached;
      }
    }
  }

  private boolean isFresh() {
    return cached != null && (System.currentTimeMillis() - lastFetch) < ttlMillis;
  }
}
