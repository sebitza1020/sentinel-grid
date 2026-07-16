package com.defense.sentinel.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.defense.sentinel.Geofence;
import java.util.List;
import org.junit.jupiter.api.Test;

class NavigationServiceTest {

  private static final double[][] SQUARE = {{0, 0}, {0, 2}, {2, 2}, {2, 0}};

  private NavigationService withZones(Geofence... zones) {
    GeofenceService geofences = new GeofenceService();
    geofences.zones.addAll(List.of(zones));
    NavigationService navigation = new NavigationService();
    navigation.geofenceService = geofences;
    return navigation;
  }

  @Test
  void detectsOnlySegmentsWhoseHorizontalAndVerticalIntervalsOverlap() {
    Geofence zone = new Geofence("restricted", SQUARE, 100, 200);

    assertFalse(
        NavigationService.segmentHitsVolume(
            new double[] {1, -1, 50}, new double[] {1, 4, 50}, zone));
    assertFalse(
        NavigationService.segmentHitsVolume(
            new double[] {1, -1, 250}, new double[] {1, 4, 250}, zone));
    assertTrue(
        NavigationService.segmentHitsVolume(
            new double[] {1, -1, 150}, new double[] {1, 4, 150}, zone));
    assertTrue(
        NavigationService.segmentHitsVolume(
            new double[] {1, -1, 100}, new double[] {1, 4, 100}, zone));
    assertTrue(
        NavigationService.segmentHitsVolume(
            new double[] {1, -1, 50}, new double[] {1, 4, 250}, zone));
  }

  @Test
  void directOverflightKeepsTheRequestedThreeDimensionalDestination() {
    NavigationService navigation =
        withZones(new Geofence("restricted", SQUARE, 0, 200));
    double[] end = {1, 4, 300};

    List<double[]> route = navigation.route(new double[] {1, -1, 300}, end);

    assertTrue(route.size() == 1);
    assertArrayEquals(end, route.get(0), 1e-9);
  }

  @Test
  void blockedFlightDetoursHorizontallyAndPreservesAltitudeProfile() {
    Geofence zone = new Geofence("restricted", SQUARE, 100, 200);
    NavigationService navigation = withZones(zone);
    double[] start = {1, -1, 120};
    double[] end = {1, 4, 180};

    List<double[]> route = navigation.route(start, end);

    assertTrue(route.size() >= 2);
    assertArrayEquals(end, route.get(route.size() - 1), 1e-9);
    double[] previous = start;
    for (double[] point : route) {
      assertFalse(NavigationService.segmentHitsVolume(previous, point, zone));
      assertTrue(point[2] >= 120 && point[2] <= 180);
      previous = point;
    }
  }

  @Test
  void resolvesMultipleRestrictedVolumes() {
    Geofence first =
        new Geofence("first", new double[][] {{0, 0}, {0, 2}, {2, 2}, {2, 0}}, 0, 300);
    Geofence second =
        new Geofence("second", new double[][] {{0, 3}, {0, 5}, {2, 5}, {2, 3}}, 0, 300);
    NavigationService navigation = withZones(first, second);

    List<double[]> route =
        navigation.route(new double[] {1, -1, 150}, new double[] {1, 6, 150});

    double[] previous = {1, -1, 150};
    for (double[] point : route) {
      assertFalse(NavigationService.segmentHitsVolume(previous, point, first));
      assertFalse(NavigationService.segmentHitsVolume(previous, point, second));
      previous = point;
    }
  }

  @Test
  void rejectsInvalidCoordinatesAndUnsatisfiableStarts() {
    NavigationService navigation =
        withZones(new Geofence("restricted", SQUARE, 0, 500));

    assertThrows(
        IllegalArgumentException.class,
        () -> navigation.route(new double[] {1}, new double[] {1, 2, 3}));
    assertThrows(
        RouteBlockedException.class,
        () -> navigation.route(new double[] {1, 1, 100}, new double[] {1, 4, 100}));
  }

  @Test
  void legacyTwoDimensionalGeometryHelpersRemainCorrect() {
    assertTrue(
        NavigationService.segmentsIntersect(
            new double[] {0, 0},
            new double[] {2, 2},
            new double[] {0, 2},
            new double[] {2, 0}));
    assertTrue(NavigationService.pointInPolygon(new double[] {1, 1}, SQUARE));
    assertFalse(NavigationService.pointInPolygon(new double[] {5, 5}, SQUARE));
  }
}
