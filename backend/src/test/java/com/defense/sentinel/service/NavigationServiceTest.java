package com.defense.sentinel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.defense.sentinel.Geofence;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Plain unit test (no @QuarkusTest) for the evasion routing + geometry primitives. Constructs the
 * services directly and injects a GeofenceService via the package-private field — the same testable
 * pattern as WeatherServiceTest.
 */
class NavigationServiceTest {

  // A 2x2 square No-Fly Zone spanning lat 0..2, lng 0..2 (vertices are [lat, lng]).
  private static final double[][] SQUARE = {{0, 0}, {0, 2}, {2, 2}, {2, 0}};

  private NavigationService withZone(double[][]... polygons) {
    GeofenceService geofences = new GeofenceService();
    for (double[][] poly : polygons) {
      geofences.zones.add(new Geofence("z" + geofences.zones.size(), poly));
    }
    NavigationService nav = new NavigationService();
    nav.geofenceService = geofences;
    return nav;
  }

  @Test
  void segmentIntersectionAndContainmentAreCorrect() {
    // Crossing "X" segments intersect; parallel offset ones do not.
    assertTrue(NavigationService.segmentsIntersect(
        new double[] {0, 0}, new double[] {2, 2}, new double[] {0, 2}, new double[] {2, 0}));
    assertFalse(NavigationService.segmentsIntersect(
        new double[] {0, 0}, new double[] {0, 2}, new double[] {2, 0}, new double[] {2, 2}));

    assertTrue(NavigationService.pointInPolygon(new double[] {1, 1}, SQUARE));
    assertFalse(NavigationService.pointInPolygon(new double[] {5, 5}, SQUARE));

    // A line straight through the square's middle is blocked.
    assertTrue(NavigationService.segmentHitsPolygon(new double[] {1, -1}, new double[] {1, 4}, SQUARE));
    // A line well clear of it is not.
    assertFalse(
        NavigationService.segmentHitsPolygon(new double[] {-5, -5}, new double[] {-5, 5}, SQUARE));
  }

  @Test
  void directPathIsReturnedWhenUnobstructed() {
    NavigationService nav = withZone(SQUARE);
    double[] start = {-5, -5};
    double[] end = {-5, 5};

    List<double[]> route = nav.route(start, end);

    assertEquals(1, route.size(), "clear path should be a single hop to the target");
    assertEquals(end, route.get(route.size() - 1));
  }

  @Test
  void blockedPathDetoursAroundTheZone() {
    NavigationService nav = withZone(SQUARE);
    double[] start = {1, -1}; // left of the zone, mid-height
    double[] end = {1, 4}; // right of the zone — direct line cuts straight through

    List<double[]> route = nav.route(start, end);

    assertTrue(route.size() >= 2, "a blocked path must include at least one detour waypoint");
    assertEquals(end, route.get(route.size() - 1), "route must still end at the target");

    // Every leg of the full path (start -> waypoints -> end) must clear the No-Fly Zone.
    double[] prev = start;
    for (double[] point : route) {
      assertFalse(
          NavigationService.segmentHitsPolygon(prev, point, SQUARE),
          "no leg of the evasion route may cross the zone");
      prev = point;
    }
  }
}
