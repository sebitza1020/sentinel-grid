package com.defense.sentinel;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DroneModelTest {
  private static Validator validator;

  @BeforeAll
  static void setUpValidator() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  @Test
  void acceptsAndMapsACompleteDossier() {
    DroneCreateRequest request = validRequest();

    assertTrue(validator.validate(request).isEmpty());

    Drone drone = Drone.create(request);
    assertEquals("SPECTRE-99", drone.callSign);
    assertEquals("SPECTRE-IV", drone.model);
    assertEquals(DroneClass.RECON, drone.modelClass);
    assertEquals(245.0, drone.topSpeed);
    assertEquals(32000.0, drone.radarRange);
    assertEquals(2.0, drone.payloadCapacity);
    assertEquals(14000, drone.batteryCapacity);
    assertArrayEquals(new String[] {"Standard", "Thermal"}, drone.visionModes);
    assertEquals("OFFLINE", drone.status);
    assertNotNull(drone.createdAt);
  }

  @Test
  void rejectsInvalidSpecificationsAndVisionModes() {
    DroneCreateRequest request = validRequest();
    request.topSpeed = 0.0;
    request.radarRange = -1.0;
    request.payloadCapacity = 0.0;
    request.batteryCapacity = 0;
    request.visionModes = new String[] {"Standard", "  "};

    assertTrue(validator.validate(request).size() >= 5);
  }

  @Test
  void appliesEveryMutableDossierField() {
    Drone drone = Drone.create(validRequest());
    DroneUpdateRequest update = new DroneUpdateRequest();
    update.callSign = "viper-88";
    update.model = " VIPER-A8 ";
    update.status = "active";
    update.modelClass = DroneClass.INTERCEPTOR;
    update.topSpeed = 440.0;
    update.radarRange = 14000.0;
    update.payloadCapacity = 6.0;
    update.batteryCapacity = 15500;
    update.visionModes = new String[] {"Standard", "Infrared", "Infrared"};

    assertTrue(validator.validate(update).isEmpty());
    drone.apply(update);

    assertEquals("VIPER-88", drone.callSign);
    assertEquals("VIPER-A8", drone.model);
    assertEquals("ACTIVE", drone.status);
    assertEquals(DroneClass.INTERCEPTOR, drone.modelClass);
    assertEquals(440.0, drone.topSpeed);
    assertEquals(14000.0, drone.radarRange);
    assertEquals(6.0, drone.payloadCapacity);
    assertEquals(15500, drone.batteryCapacity);
    assertArrayEquals(new String[] {"Standard", "Infrared"}, drone.visionModes);
  }

  @Test
  void migrationDefinesBalancedNonDestructiveCatalog() throws IOException {
    String sql;
    try (var stream = getClass()
        .getResourceAsStream("/db/migration/V1__baseline_and_omniscience_fleet.sql")) {
      assertNotNull(stream);
      sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }

    String catalog = sql.substring(sql.indexOf(") VALUES") + 8, sql.indexOf("ON CONFLICT"));
    assertEquals(21, count(catalog, Pattern.compile("00000000-0000-3500-8000-0000000000\\d{2}")));
    assertEquals(7, count(catalog, Pattern.compile("'RECON'")));
    assertEquals(7, count(catalog, Pattern.compile("'INTERCEPTOR'")));
    assertEquals(7, count(catalog, Pattern.compile("'HEAVY_SUPPORT'")));
    assertTrue(sql.contains("ON CONFLICT (call_sign) DO UPDATE"));
    assertFalse(sql.toUpperCase().contains("DELETE FROM DRONES"));
    assertFalse(sql.toUpperCase().contains("TRUNCATE"));
  }

  private static int count(String text, Pattern pattern) {
    int count = 0;
    Matcher matcher = pattern.matcher(text);
    while (matcher.find()) count++;
    return count;
  }

  private static DroneCreateRequest validRequest() {
    DroneCreateRequest request = new DroneCreateRequest();
    request.callSign = "spectre-99";
    request.model = "SPECTRE-IV";
    request.modelClass = DroneClass.RECON;
    request.topSpeed = 245.0;
    request.radarRange = 32000.0;
    request.payloadCapacity = 2.0;
    request.batteryCapacity = 14000;
    request.visionModes = new String[] {"Standard", "Thermal"};
    return request;
  }
}
