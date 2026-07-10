package com.defense.sentinel.service;

import com.defense.sentinel.Geofence;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Waypoint vector calculator. Before a drone flies a straight line to its target, this checks whether
 * the direct segment crosses any active No-Fly Zone and, if so, computes a simple evasion route that
 * detours around the blocking polygon's vertices.
 *
 * <p>All geometry is plain 2D math on {@code [lat, lng]} pairs — at Bucharest scale a planar
 * approximation is more than accurate enough for tactical routing, and it keeps the logic dependency-
 * free and unit-testable (no Quarkus boot, no JTS).
 */
@ApplicationScoped
public class NavigationService {

  // Package-private so a plain-JUnit test can inject a GeofenceService directly (WeatherService style).
  @Inject GeofenceService geofenceService;

  // How far outside a blocking polygon's vertices we route, as a fraction of the vertex's distance
  // from the polygon centroid (gives the drone clearance instead of grazing the boundary).
  static final double CLEARANCE = 0.15;

  /**
   * Returns the ordered waypoints (each {@code [lat, lng]}) the drone should fly through to reach
   * {@code end} from {@code start} without entering a restricted zone. Always ends with {@code end};
   * returns just {@code [end]} when the direct path is clear.
   */
  public List<double[]> route(double[] start, double[] end) {
    List<double[]> result = new ArrayList<>();
    Geofence blocking = firstBlockingZone(start, end);
    if (blocking != null) {
      result.addAll(detourChain(start, end, blocking.polygon));
    }
    result.add(end);
    return result;
  }

  private Geofence firstBlockingZone(double[] start, double[] end) {
    for (Geofence zone : geofenceService.getZones()) {
      if (zone.polygon != null && zone.polygon.length >= 3 && segmentHitsPolygon(start, end, zone.polygon)) {
        return zone;
      }
    }
    return null;
  }

  /**
   * Detour around the blocking polygon by hugging its boundary: route through the outward-offset
   * vertices along the shorter perimeter chain from the vertex nearest {@code start} to the vertex
   * nearest {@code end}. For a convex zone this reliably clears the obstacle (even when the direct
   * line passes straight through the middle, where no single-corner detour exists).
   */
  private List<double[]> detourChain(double[] start, double[] end, double[][] polygon) {
    int n = polygon.length;
    double[] centroid = centroid(polygon);
    double[][] offsets = new double[n][];
    for (int i = 0; i < n; i++) {
      offsets[i] = offsetFromCentroid(polygon[i], centroid, CLEARANCE);
    }

    int s = nearestVertex(polygon, start);
    int e = nearestVertex(polygon, end);

    List<double[]> forward = chain(offsets, s, e, 1);
    List<double[]> backward = chain(offsets, s, e, -1);
    return pathLength(start, forward, end) <= pathLength(start, backward, end) ? forward : backward;
  }

  private static int nearestVertex(double[][] polygon, double[] p) {
    int best = 0;
    double bestDist = Double.MAX_VALUE;
    for (int i = 0; i < polygon.length; i++) {
      double d = distance(polygon[i], p);
      if (d < bestDist) {
        bestDist = d;
        best = i;
      }
    }
    return best;
  }

  /** Offset vertices from index {@code s} to {@code e} walking the polygon in {@code step} direction. */
  private static List<double[]> chain(double[][] offsets, int s, int e, int step) {
    int n = offsets.length;
    List<double[]> out = new ArrayList<>();
    int i = s;
    while (true) {
      out.add(offsets[i]);
      if (i == e) {
        break;
      }
      i = (i + step + n) % n;
    }
    return out;
  }

  private static double pathLength(double[] start, List<double[]> waypoints, double[] end) {
    double total = 0;
    double[] prev = start;
    for (double[] w : waypoints) {
      total += distance(prev, w);
      prev = w;
    }
    return total + distance(prev, end);
  }

  // --- Pure geometry helpers (package-private for direct unit testing) ---

  /** True if segment a-b crosses any edge of the polygon or either endpoint lies inside it. */
  static boolean segmentHitsPolygon(double[] a, double[] b, double[][] polygon) {
    if (pointInPolygon(a, polygon) || pointInPolygon(b, polygon)) {
      return true;
    }
    for (int i = 0; i < polygon.length; i++) {
      double[] c = polygon[i];
      double[] d = polygon[(i + 1) % polygon.length];
      if (segmentsIntersect(a, b, c, d)) {
        return true;
      }
    }
    return false;
  }

  /** Standard orientation (cross-product) test for whether segments a-b and c-d intersect. */
  static boolean segmentsIntersect(double[] a, double[] b, double[] c, double[] d) {
    double d1 = cross(c, d, a);
    double d2 = cross(c, d, b);
    double d3 = cross(a, b, c);
    double d4 = cross(a, b, d);
    if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
      return true;
    }
    // Colinear touch cases.
    return (d1 == 0 && onSegment(c, d, a))
        || (d2 == 0 && onSegment(c, d, b))
        || (d3 == 0 && onSegment(a, b, c))
        || (d4 == 0 && onSegment(a, b, d));
  }

  /** Ray-casting point-in-polygon test. */
  static boolean pointInPolygon(double[] p, double[][] polygon) {
    boolean inside = false;
    for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
      double yi = polygon[i][0];
      double xi = polygon[i][1];
      double yj = polygon[j][0];
      double xj = polygon[j][1];
      boolean straddles = (xi > p[1]) != (xj > p[1]);
      if (straddles && p[0] < (yj - yi) * (p[1] - xi) / (xj - xi) + yi) {
        inside = !inside;
      }
    }
    return inside;
  }

  private static double cross(double[] o, double[] a, double[] b) {
    return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
  }

  private static boolean onSegment(double[] a, double[] b, double[] p) {
    return Math.min(a[0], b[0]) <= p[0]
        && p[0] <= Math.max(a[0], b[0])
        && Math.min(a[1], b[1]) <= p[1]
        && p[1] <= Math.max(a[1], b[1]);
  }

  private static double[] centroid(double[][] polygon) {
    double lat = 0;
    double lng = 0;
    for (double[] v : polygon) {
      lat += v[0];
      lng += v[1];
    }
    return new double[] {lat / polygon.length, lng / polygon.length};
  }

  private static double[] offsetFromCentroid(double[] vertex, double[] centroid, double fraction) {
    return new double[] {
      vertex[0] + (vertex[0] - centroid[0]) * fraction, vertex[1] + (vertex[1] - centroid[1]) * fraction
    };
  }

  private static double distance(double[] a, double[] b) {
    double dLat = a[0] - b[0];
    double dLng = a[1] - b[1];
    return Math.sqrt(dLat * dLat + dLng * dLng);
  }
}
