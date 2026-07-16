package com.defense.sentinel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.defense.sentinel.WeatherDTO;
import com.defense.sentinel.websocket.TelemetrySocket;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Plain unit test (no @QuarkusTest) for the Mission Debrief PDF generator and the session verdict
 * log. Mocks the fleet pool + weather (FleetCommanderServiceTest pattern) but exercises REAL OpenPDF
 * rendering — the assertions run against actual PDF bytes.
 */
class MissionDebriefServiceTest {

  private static Map<String, Object> unit(double lat, double lng, double alt, int battery, String threat) {
    Map<String, Object> state = new HashMap<>();
    state.put("lat", lat);
    state.put("lng", lng);
    state.put("alt", alt);
    state.put("battery", battery);
    if (threat != null) {
      state.put("threat_level", threat);
    }
    return state;
  }

  private MissionDebriefService newService(Map<String, Map<String, Object>> pool, WeatherDTO weather) {
    TelemetrySocket socket = mock(TelemetrySocket.class);
    when(socket.currentStates()).thenReturn(pool);
    WeatherService weatherService = mock(WeatherService.class);
    when(weatherService.getBucharestWeather()).thenReturn(weather);

    MissionDebriefService service = new MissionDebriefService();
    service.telemetrySocket = socket;
    service.weatherService = weatherService;
    return service;
  }

  @Test
  void generatesRealPdfWithFleetWeatherLogAndVectorBranding() throws Exception {
    Map<String, Map<String, Object>> pool = new HashMap<>();
    pool.put("Falcon-1", unit(44.43, 26.10, 180, 90, "SAFE"));
    pool.put("Ghost-2", unit(44.45, 26.12, 210, 40, "THREAT"));

    WeatherDTO weather = new WeatherDTO();
    weather.current = new WeatherDTO.Current();
    weather.current.temperature_2m = 27.3;
    weather.current.wind_speed_10m = 4.7;
    weather.current.relative_humidity_2m = 38;

    MissionDebriefService service = newService(pool, weather);
    service.recordVerdict("Ghost-2", "THREAT", "Visual contact: Armed convoy.");
    service.recordVerdict("Falcon-1", "SAFE", "Sector clear.");

    byte[] pdf = service.generateDebriefPdf();

    String magic = new String(pdf, 0, 5, StandardCharsets.US_ASCII);
    assertEquals("%PDF-", magic, "output must be a real PDF document");
    assertTrue(pdf.length > 1500, "debrief should contain rendered content, got " + pdf.length + " bytes");

    PdfReader reader = new PdfReader(pdf);
    try {
      assertEquals(1, reader.getNumberOfPages());
      String pageText = new PdfTextExtractor(reader).getTextFromPage(1);
      assertTrue(pageText.contains("SENTINEL GRID // OFFICIAL TACTICAL DEBRIEF"));

      String pageContent = new String(reader.getPageContent(1), StandardCharsets.ISO_8859_1);
      assertTrue(pageContent.contains("0.95294"), "content stream must include the cyan emblem color");
      assertTrue(
          pageContent.contains(" m\n") && pageContent.contains(" l\n"),
          "content stream must include vector emblem paths");
    } finally {
      reader.close();
    }
  }

  @Test
  void handlesEmptyPoolAndOfflineWeather() {
    MissionDebriefService service = newService(new HashMap<>(), null);

    byte[] pdf = service.generateDebriefPdf();

    assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.US_ASCII));
  }

  @Test
  void threatLogIsChronologicalAndBounded() {
    MissionDebriefService service = newService(new HashMap<>(), null);

    for (int i = 0; i < MissionDebriefService.MAX_LOG_ENTRIES + 25; i++) {
      service.recordVerdict("UNIT-" + i, "SAFE", "tick " + i);
    }

    assertEquals(MissionDebriefService.MAX_LOG_ENTRIES, service.threatLog.size(), "log must stay bounded");
    assertEquals("UNIT-25", service.threatLog.peekFirst().callSign, "oldest overflow entries must drop");
    assertEquals(
        "UNIT-" + (MissionDebriefService.MAX_LOG_ENTRIES + 24),
        service.threatLog.peekLast().callSign,
        "newest entry must be last (chronological order)");
  }
}
