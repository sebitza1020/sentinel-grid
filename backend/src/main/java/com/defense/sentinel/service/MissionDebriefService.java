package com.defense.sentinel.service;

import com.defense.sentinel.WeatherDTO;
import com.defense.sentinel.websocket.TelemetrySocket;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Mission Debrief generator. Collects the live fleet snapshot (TelemetrySocket pool), the cached
 * atmospheric readings, and a session-scoped chronological log of AI verdicts, and renders them as
 * an official tactical-debrief PDF (OpenPDF).
 *
 * <p>The verdict log lives in active session memory (bounded deque) — the platform keeps no
 * historical telemetry store, and the %test profile runs without a datasource, so in-memory is both
 * the simplest and the test-safe choice. It resets on redeploy.
 */
@ApplicationScoped
public class MissionDebriefService {

  static final int MAX_LOG_ENTRIES = 200;

  private static final DateTimeFormatter TS_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  // Cyberpunk-on-paper palette: dark ink + cyan-ish accent kept print-friendly.
  private static final Color INK = new Color(16, 24, 32);
  private static final Color ACCENT = new Color(0, 120, 128);
  private static final Color HEADER_BG = new Color(16, 24, 32);
  private static final Color ROW_SHADE = new Color(236, 242, 244);

  @Inject TelemetrySocket telemetrySocket;

  @Inject WeatherService weatherService;

  // Jurnalul cronologic al verdictelor AI din sesiunea curentă (cel mai vechi primul).
  final Deque<ThreatLogEntry> threatLog = new ConcurrentLinkedDeque<>();

  /** One timestamped AI verdict, as recorded when a drone report was analyzed. */
  public static class ThreatLogEntry {
    public final Instant timestamp;
    public final String callSign;
    public final String verdict;
    public final String report;

    ThreatLogEntry(Instant timestamp, String callSign, String verdict, String report) {
      this.timestamp = timestamp;
      this.callSign = callSign;
      this.verdict = verdict;
      this.report = report;
    }
  }

  /** Cheap inline append from the telemetry ping flow; oldest entries drop past the cap. */
  public void recordVerdict(String callSign, String verdict, String report) {
    threatLog.addLast(new ThreatLogEntry(Instant.now(), callSign, verdict, report));
    while (threatLog.size() > MAX_LOG_ENTRIES) {
      threatLog.pollFirst();
    }
  }

  /** Renders the full tactical debrief and returns the PDF bytes. */
  public byte[] generateDebriefPdf() {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      Document document = new Document(PageSize.A4, 40, 40, 50, 40);
      PdfWriter.getInstance(document, out);
      document.open();

      addHeader(document);
      addFleetSection(document);
      addWeatherSection(document);
      addThreatLogSection(document);

      document.close();
      return out.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException("[DEBRIEF] PDF generation failed: " + e.getMessage(), e);
    }
  }

  // --- Sections ---

  private void addHeader(Document document) {
    Paragraph title =
        new Paragraph(
            "SENTINEL GRID // OFFICIAL TACTICAL DEBRIEF",
            new Font(Font.COURIER, 16, Font.BOLD, INK));
    title.setSpacingAfter(2);
    document.add(title);

    String generated = LocalDateTime.now().format(TS_FORMAT);
    Paragraph stamp =
        new Paragraph("GENERATED: " + generated + " LOCAL", new Font(Font.COURIER, 9, Font.NORMAL, ACCENT));
    stamp.setSpacingAfter(14);
    document.add(stamp);
  }

  private void addFleetSection(Document document) {
    sectionHeading(document, "1. FLEET STATUS SUMMARY");

    // TreeMap => call signs sortate, output stabil.
    Map<String, Map<String, Object>> pool = new TreeMap<>(telemetrySocket.currentStates());

    int units = pool.size();
    List<Integer> batteries = new ArrayList<>();
    double maxAlt = 0;
    int threats = 0;
    for (Map<String, Object> state : pool.values()) {
      Integer battery = asInt(state.get("battery"));
      if (battery != null) {
        batteries.add(battery);
      }
      Double alt = asDouble(state.get("alt"));
      if (alt != null && alt > maxAlt) {
        maxAlt = alt;
      }
      if ("THREAT".equals(state.get("threat_level"))) {
        threats++;
      }
    }
    int avgBattery =
        batteries.isEmpty()
            ? 0
            : (int) Math.round(batteries.stream().mapToInt(Integer::intValue).average().orElse(0));

    document.add(
        monoParagraph(
            String.format(
                "ACTIVE UNITS: %d   AVG BATTERY: %d%%   MAX ALTITUDE: %.0f m   THREATS ACTIVE: %d",
                units, avgBattery, maxAlt, threats)));

    if (pool.isEmpty()) {
      document.add(monoParagraph("No live telemetry recorded this session."));
      return;
    }

    PdfPTable table = newTable(new float[] {2.2f, 1.8f, 1.8f, 1.2f, 1.4f, 1.8f});
    headerRow(table, "CALL SIGN", "LAT", "LNG", "ALT", "BATTERY", "THREAT LEVEL");
    int row = 0;
    for (Map.Entry<String, Map<String, Object>> entry : pool.entrySet()) {
      Map<String, Object> s = entry.getValue();
      boolean shade = row++ % 2 == 1;
      dataRow(
          table,
          shade,
          entry.getKey().toUpperCase(),
          coord(asDouble(s.get("lat"))),
          coord(asDouble(s.get("lng"))),
          asDouble(s.get("alt")) != null ? String.format("%.0f m", asDouble(s.get("alt"))) : "--",
          asInt(s.get("battery")) != null ? asInt(s.get("battery")) + "%" : "--",
          s.get("threat_level") != null ? String.valueOf(s.get("threat_level")) : "PENDING");
    }
    document.add(table);
  }

  private void addWeatherSection(Document document) {
    sectionHeading(document, "2. ATMOSPHERIC ENVIRONMENT RECORDS");

    WeatherDTO weather = weatherService.getBucharestWeather();
    PdfPTable table = newTable(new float[] {3f, 2f});
    headerRow(table, "SENSOR // BUCHAREST AO", "READING");
    if (weather != null && weather.current != null) {
      dataRow(table, false, "TEMPERATURE", String.format("%.1f °C", weather.current.temperature_2m));
      dataRow(table, true, "WIND SPEED", String.format("%.1f km/h", weather.current.wind_speed_10m));
      dataRow(table, false, "RELATIVE HUMIDITY", weather.current.relative_humidity_2m + " %");
    } else {
      dataRow(table, false, "ATMOSPHERIC SENSORS", "OFFLINE — NO DATA AVAILABLE");
    }
    document.add(table);
  }

  private void addThreatLogSection(Document document) {
    sectionHeading(document, "3. AI CHRONOLOGICAL THREAT LOG");

    if (threatLog.isEmpty()) {
      document.add(monoParagraph("No AI verdicts recorded this session."));
      return;
    }

    PdfPTable table = newTable(new float[] {2.2f, 1.8f, 1.4f, 5f});
    headerRow(table, "TIMESTAMP (UTC)", "UNIT", "VERDICT", "FIELD REPORT");
    int row = 0;
    for (ThreatLogEntry entry : threatLog) {
      String ts = TS_FORMAT.format(LocalDateTime.ofInstant(entry.timestamp, ZoneId.of("UTC")));
      dataRow(
          table,
          row++ % 2 == 1,
          ts,
          entry.callSign.toUpperCase(),
          entry.verdict,
          entry.report != null ? entry.report : "--");
    }
    document.add(table);
  }

  // --- PDF building blocks ---

  private void sectionHeading(Document document, String text) {
    Paragraph heading = new Paragraph(text, new Font(Font.COURIER, 12, Font.BOLD, ACCENT));
    heading.setSpacingBefore(14);
    heading.setSpacingAfter(6);
    document.add(heading);
  }

  private Paragraph monoParagraph(String text) {
    Paragraph p = new Paragraph(text, new Font(Font.COURIER, 9, Font.NORMAL, INK));
    p.setSpacingAfter(6);
    return p;
  }

  private PdfPTable newTable(float[] widths) {
    PdfPTable table = new PdfPTable(widths);
    table.setWidthPercentage(100);
    table.setSpacingBefore(4);
    table.setSpacingAfter(8);
    return table;
  }

  private void headerRow(PdfPTable table, String... labels) {
    for (String label : labels) {
      PdfPCell cell = new PdfPCell(
          new Paragraph(label, new Font(Font.COURIER, 8, Font.BOLD, Color.WHITE)));
      cell.setBackgroundColor(HEADER_BG);
      cell.setPadding(5);
      cell.setBorderColor(HEADER_BG);
      cell.setHorizontalAlignment(Element.ALIGN_LEFT);
      table.addCell(cell);
    }
  }

  private void dataRow(PdfPTable table, boolean shaded, String... values) {
    for (String value : values) {
      PdfPCell cell =
          new PdfPCell(new Paragraph(value, new Font(Font.COURIER, 8, Font.NORMAL, INK)));
      cell.setPadding(4);
      cell.setBorderColor(new Color(200, 210, 214));
      if (shaded) {
        cell.setBackgroundColor(ROW_SHADE);
      }
      table.addCell(cell);
    }
  }

  private static String coord(Double value) {
    return value != null ? String.format("%.4f", value) : "--";
  }

  // Coerciție tolerantă: valorile din pool sunt Object (Jackson poate da Integer/Double/Long).
  private static Integer asInt(Object value) {
    return value instanceof Number ? ((Number) value).intValue() : null;
  }

  private static Double asDouble(Object value) {
    return value instanceof Number ? ((Number) value).doubleValue() : null;
  }
}
