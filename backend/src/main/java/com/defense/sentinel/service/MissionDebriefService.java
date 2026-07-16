package com.defense.sentinel.service;

import com.defense.sentinel.WeatherDTO;
import com.defense.sentinel.websocket.TelemetrySocket;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfContentByte;
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
  private static final Color EMBLEM_NAVY = new Color(7, 21, 33);
  private static final Color EMBLEM_CYAN = new Color(0, 243, 255);
  private static final Color EMBLEM_SWEEP = new Color(172, 249, 255);
  private static final float EMBLEM_SCALE = 0.55f;

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
      PdfWriter writer = PdfWriter.getInstance(document, out);
      document.open();

      addHeader(document, writer);
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

  private void addHeader(Document document, PdfWriter writer) {
    drawEmblem(
        writer.getDirectContent(),
        document.left(),
        document.top() - (64 * EMBLEM_SCALE),
        EMBLEM_SCALE);

    Paragraph title =
        new Paragraph(
            "SENTINEL GRID // OFFICIAL TACTICAL DEBRIEF",
            new Font(Font.COURIER, 16, Font.BOLD, INK));
    title.setIndentationLeft(62);
    title.setSpacingAfter(2);
    document.add(title);

    String generated = LocalDateTime.now().format(TS_FORMAT);
    Paragraph stamp =
        new Paragraph("GENERATED: " + generated + " LOCAL", new Font(Font.COURIER, 9, Font.NORMAL, ACCENT));
    stamp.setIndentationLeft(62);
    stamp.setSpacingAfter(14);
    document.add(stamp);
  }

  /** Draws the same normalized 96x64 winged radar-shield used by the Angular logo. */
  private void drawEmblem(PdfContentByte canvas, float originX, float originY, float scale) {
    canvas.saveState();
    canvas.setColorStroke(EMBLEM_CYAN);
    canvas.setColorFill(EMBLEM_NAVY);
    canvas.setLineWidth(0.8f);

    drawPolygon(
        canvas,
        originX,
        originY,
        scale,
        new float[][] {
          {36, 15}, {23, 11}, {6, 19}, {21, 23}, {3, 31}, {28, 33}, {13, 43}, {38, 36}
        });
    drawPolygon(
        canvas,
        originX,
        originY,
        scale,
        new float[][] {
          {60, 15}, {73, 11}, {90, 19}, {75, 23}, {93, 31}, {68, 33}, {83, 43}, {58, 36}
        });

    // Shield shell.
    canvas.moveTo(x(originX, scale, 48), y(originY, scale, 4));
    canvas.lineTo(x(originX, scale, 64), y(originY, scale, 11));
    canvas.lineTo(x(originX, scale, 61.5f), y(originY, scale, 38));
    canvas.curveTo(
        x(originX, scale, 60),
        y(originY, scale, 48),
        x(originX, scale, 54.5f),
        y(originY, scale, 55),
        x(originX, scale, 48),
        y(originY, scale, 60));
    canvas.curveTo(
        x(originX, scale, 41.5f),
        y(originY, scale, 55),
        x(originX, scale, 36),
        y(originY, scale, 48),
        x(originX, scale, 34.5f),
        y(originY, scale, 38));
    canvas.lineTo(x(originX, scale, 32), y(originY, scale, 11));
    canvas.closePathFillStroke();

    // Shield inset and wing circuit traces.
    canvas.setLineWidth(0.4f);
    strokePolyline(
        canvas,
        originX,
        originY,
        scale,
        new float[][] {{48, 10}, {58, 14.5f}, {56.2f, 36.3f}, {48, 51.5f}, {39.8f, 36.3f}, {38, 14.5f}, {48, 10}});
    strokeLine(canvas, originX, originY, scale, 31, 21, 17, 19);
    strokeLine(canvas, originX, originY, scale, 32, 28, 12, 28);
    strokeLine(canvas, originX, originY, scale, 32, 34, 22, 39);
    strokeLine(canvas, originX, originY, scale, 65, 21, 79, 19);
    strokeLine(canvas, originX, originY, scale, 64, 28, 84, 28);
    strokeLine(canvas, originX, originY, scale, 64, 34, 74, 39);

    // Radar rings, reticle, and static sweep.
    canvas.setLineWidth(0.65f);
    canvas.circle(x(originX, scale, 48), y(originY, scale, 29), 12 * scale);
    canvas.stroke();
    canvas.setLineWidth(0.4f);
    canvas.circle(x(originX, scale, 48), y(originY, scale, 29), 7 * scale);
    canvas.stroke();
    strokeLine(canvas, originX, originY, scale, 36, 29, 60, 29);
    strokeLine(canvas, originX, originY, scale, 48, 17, 48, 41);
    strokeLine(canvas, originX, originY, scale, 39.5f, 20.5f, 56.5f, 37.5f);
    strokeLine(canvas, originX, originY, scale, 56.5f, 20.5f, 39.5f, 37.5f);

    canvas.setColorFill(EMBLEM_SWEEP);
    canvas.moveTo(x(originX, scale, 48), y(originY, scale, 29));
    canvas.lineTo(x(originX, scale, 56.5f), y(originY, scale, 20.5f));
    canvas.lineTo(x(originX, scale, 60), y(originY, scale, 29));
    canvas.closePathFillStroke();
    canvas.setColorFill(EMBLEM_CYAN);
    canvas.circle(x(originX, scale, 48), y(originY, scale, 29), 2 * scale);
    canvas.fill();

    strokeLine(canvas, originX, originY, scale, 44, 57, 52, 57);
    strokeLine(canvas, originX, originY, scale, 46, 61, 50, 61);
    canvas.restoreState();
  }

  private static void drawPolygon(
      PdfContentByte canvas, float originX, float originY, float scale, float[][] points) {
    canvas.moveTo(x(originX, scale, points[0][0]), y(originY, scale, points[0][1]));
    for (int i = 1; i < points.length; i++) {
      canvas.lineTo(x(originX, scale, points[i][0]), y(originY, scale, points[i][1]));
    }
    canvas.closePathFillStroke();
  }

  private static void strokePolyline(
      PdfContentByte canvas, float originX, float originY, float scale, float[][] points) {
    canvas.moveTo(x(originX, scale, points[0][0]), y(originY, scale, points[0][1]));
    for (int i = 1; i < points.length; i++) {
      canvas.lineTo(x(originX, scale, points[i][0]), y(originY, scale, points[i][1]));
    }
    canvas.stroke();
  }

  private static void strokeLine(
      PdfContentByte canvas,
      float originX,
      float originY,
      float scale,
      float x1,
      float y1,
      float x2,
      float y2) {
    canvas.moveTo(x(originX, scale, x1), y(originY, scale, y1));
    canvas.lineTo(x(originX, scale, x2), y(originY, scale, y2));
    canvas.stroke();
  }

  private static float x(float originX, float scale, float value) {
    return originX + (value * scale);
  }

  private static float y(float originY, float scale, float value) {
    return originY + ((64 - value) * scale);
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
