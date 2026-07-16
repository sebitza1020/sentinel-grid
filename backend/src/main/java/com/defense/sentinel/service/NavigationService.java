package com.defense.sentinel.service;

import com.defense.sentinel.Geofence;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Three-dimensional waypoint calculator. Coordinates are {@code [lat, lng, altitudeAgl]}; legacy
 * {@code [lat, lng]} inputs are normalized to ground level. Restricted airspace is represented as a
 * polygon prism whose horizontal footprint and altitude interval must both overlap a flight segment
 * for the route to be blocked.
 */
@ApplicationScoped
public class NavigationService {

  @Inject GeofenceService geofenceService;

  static final double CLEARANCE = 0.15;
  static final int MAX_DETOURS = 32;
  private static final double EPSILON = 1e-9;

  /**
   * Returns ordered 3D waypoints that reach {@code end} without entering any active restricted
   * volume. A clear route contains only the normalized destination.
   */
  public List<double[]> route(double[] start, double[] end) {
    double[] normalizedStart = normalizePosition(start);
    double[] normalizedEnd = normalizePosition(end);
    List<double[]> path = new ArrayList<>();
    path.add(normalizedStart);
    path.add(normalizedEnd);

    for (int attempt = 0; attempt < MAX_DETOURS; attempt++) {
      Collision collision = firstCollision(path);
      if (collision == null) {
        return new ArrayList<>(path.subList(1, path.size()));
      }

      double[] legStart = path.get(collision.legIndex());
      double[] legEnd = path.get(collision.legIndex() + 1);
      List<double[]> detour =
          elevateDetour(
              legStart, legEnd, detourChain(legStart, legEnd, collision.zone().polygon));
      if (detour.isEmpty()) {
        break;
      }
      path.addAll(collision.legIndex() + 1, detour);
    }

    throw new RouteBlockedException(
        "No collision-free route could be generated for the active restricted airspace.");
  }

  private Collision firstCollision(List<double[]> path) {
    List<Geofence> zones = geofenceService.getZones();
    for (int i = 0; i < path.size() - 1; i++) {
      for (Geofence zone : zones) {
        if (segmentHitsVolume(path.get(i), path.get(i + 1), zone)) {
          return new Collision(i, zone);
        }
      }
    }
    return null;
  }

  private record Collision(int legIndex, Geofence zone) {}

  /**
   * Detours around the blocking footprint by following outward-offset vertices on the shorter
   * perimeter chain.
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
    return pathLength(start, forward, end) <= pathLength(start, backward, end)
        ? forward
        : backward;
  }

  /** Preserve the leg's requested climb/descent profile across generated horizontal waypoints. */
  private static List<double[]> elevateDetour(
      double[] start, double[] end, List<double[]> horizontalWaypoints) {
    List<double[]> all = new ArrayList<>();
    all.add(start);
    all.addAll(horizontalWaypoints);
    all.add(end);

    double total = 0;
    for (int i = 1; i < all.size(); i++) {
      total += distance(all.get(i - 1), all.get(i));
    }

    List<double[]> elevated = new ArrayList<>();
    double travelled = 0;
    for (int i = 1; i < all.size() - 1; i++) {
      travelled += distance(all.get(i - 1), all.get(i));
      double fraction = total <= EPSILON ? i / (double) (all.size() - 1) : travelled / total;
      double[] point = all.get(i);
      elevated.add(
          new double[] {
            point[0], point[1], start[2] + (end[2] - start[2]) * fraction
          });
    }
    return elevated;
  }

  private static int nearestVertex(double[][] polygon, double[] point) {
    int best = 0;
    double bestDistance = Double.MAX_VALUE;
    for (int i = 0; i < polygon.length; i++) {
      double candidate = distance(polygon[i], point);
      if (candidate < bestDistance) {
        bestDistance = candidate;
        best = i;
      }
    }
    return best;
  }

  private static List<double[]> chain(double[][] offsets, int start, int end, int step) {
    int n = offsets.length;
    List<double[]> out = new ArrayList<>();
    int i = start;
    while (true) {
      out.add(offsets[i]);
      if (i == end) {
        return out;
      }
      i = (i + step + n) % n;
    }
  }

  private static double pathLength(double[] start, List<double[]> waypoints, double[] end) {
    double total = 0;
    double[] previous = start;
    for (double[] waypoint : waypoints) {
      total += distance(previous, waypoint);
      previous = waypoint;
    }
    return total + distance(previous, end);
  }

  static double[] normalizePosition(double[] position) {
    if (position == null || (position.length != 2 && position.length != 3)) {
      throw new IllegalArgumentException(
          "Coordinates must contain [lat, lng] or [lat, lng, altitude].");
    }
    double altitude = position.length == 3 ? position[2] : 0.0;
    if (!Double.isFinite(position[0])
        || !Double.isFinite(position[1])
        || !Double.isFinite(altitude)
        || position[0] < -90
        || position[0] > 90
        || position[1] < -180
        || position[1] > 180
        || altitude < 0) {
      throw new IllegalArgumentException(
          "Route coordinates and altitude must be finite and geographically valid.");
    }
    return new double[] {position[0], position[1], altitude};
  }

  /**
   * True when the parameter interval inside the polygon footprint overlaps the parameter interval
   * inside the zone's altitude band. Polygon and altitude boundaries are restricted.
   */
  static boolean segmentHitsVolume(double[] rawStart, double[] rawEnd, Geofence zone) {
    double[] start = normalizePosition(rawStart);
    double[] end = normalizePosition(rawEnd);
    if (zone == null || zone.polygon == null || zone.polygon.length < 3) {
      return false;
    }

    double minAltitude =
        zone.minAltitude == null
            ? GeofenceService.DEFAULT_MIN_ALTITUDE
            : zone.minAltitude;
    double maxAltitude =
        zone.maxAltitude == null
            ? GeofenceService.DEFAULT_MAX_ALTITUDE
            : zone.maxAltitude;

    List<Double> parameters = footprintBreakpoints(start, end, zone.polygon);
    for (double parameter : parameters) {
      double[] point = interpolate(start, end, parameter);
      if (pointInOrOnPolygon(point, zone.polygon)
          && within(point[2], minAltitude, maxAltitude)) {
        return true;
      }
    }

    for (int i = 0; i < parameters.size() - 1; i++) {
      double from = parameters.get(i);
      double to = parameters.get(i + 1);
      if (to - from <= EPSILON) {
        continue;
      }
      double midpoint = (from + to) / 2.0;
      if (!pointInOrOnPolygon(interpolate(start, end, midpoint), zone.polygon)) {
        continue;
      }
      double altitudeFrom = interpolate(start, end, from)[2];
      double altitudeTo = interpolate(start, end, to)[2];
      if (Math.max(altitudeFrom, altitudeTo) + EPSILON >= minAltitude
          && Math.min(altitudeFrom, altitudeTo) - EPSILON <= maxAltitude) {
        return true;
      }
    }
    return false;
  }

  private static List<Double> footprintBreakpoints(
      double[] start, double[] end, double[][] polygon) {
    List<Double> parameters = new ArrayList<>();
    parameters.add(0.0);
    parameters.add(1.0);
    for (int i = 0; i < polygon.length; i++) {
      addIntersectionParameters(
          start, end, polygon[i], polygon[(i + 1) % polygon.length], parameters);
    }
    parameters.sort(Comparator.naturalOrder());

    List<Double> unique = new ArrayList<>();
    for (double value : parameters) {
      double clamped = Math.max(0.0, Math.min(1.0, value));
      if (unique.isEmpty() || Math.abs(clamped - unique.get(unique.size() - 1)) > EPSILON) {
        unique.add(clamped);
      }
    }
    return unique;
  }

  private static void addIntersectionParameters(
      double[] start, double[] end, double[] edgeStart, double[] edgeEnd, List<Double> out) {
    double rx = end[1] - start[1];
    double ry = end[0] - start[0];
    double sx = edgeEnd[1] - edgeStart[1];
    double sy = edgeEnd[0] - edgeStart[0];
    double qpx = edgeStart[1] - start[1];
    double qpy = edgeStart[0] - start[0];
    double denominator = cross2(rx, ry, sx, sy);
    double collinearity = cross2(qpx, qpy, rx, ry);

    if (Math.abs(denominator) > EPSILON) {
      double t = cross2(qpx, qpy, sx, sy) / denominator;
      double u = cross2(qpx, qpy, rx, ry) / denominator;
      if (t >= -EPSILON && t <= 1 + EPSILON && u >= -EPSILON && u <= 1 + EPSILON) {
        out.add(t);
      }
      return;
    }

    if (Math.abs(collinearity) > EPSILON) {
      return;
    }

    double lengthSquared = rx * rx + ry * ry;
    if (lengthSquared <= EPSILON) {
      return;
    }
    double edgeStartT = (qpx * rx + qpy * ry) / lengthSquared;
    double edgeEndT =
        ((edgeEnd[1] - start[1]) * rx + (edgeEnd[0] - start[0]) * ry) / lengthSquared;
    if (Math.max(Math.min(edgeStartT, edgeEndT), 0.0)
        <= Math.min(Math.max(edgeStartT, edgeEndT), 1.0) + EPSILON) {
      out.add(edgeStartT);
      out.add(edgeEndT);
    }
  }

  private static double cross2(double ax, double ay, double bx, double by) {
    return ax * by - ay * bx;
  }

  private static boolean within(double value, double min, double max) {
    return value + EPSILON >= min && value - EPSILON <= max;
  }

  private static double[] interpolate(double[] start, double[] end, double parameter) {
    return new double[] {
      start[0] + (end[0] - start[0]) * parameter,
      start[1] + (end[1] - start[1]) * parameter,
      start[2] + (end[2] - start[2]) * parameter
    };
  }

  /** Legacy 2D helper retained for focused geometry tests. */
  static boolean segmentHitsPolygon(double[] start, double[] end, double[][] polygon) {
    if (pointInOrOnPolygon(start, polygon) || pointInOrOnPolygon(end, polygon)) {
      return true;
    }
    for (int i = 0; i < polygon.length; i++) {
      if (segmentsIntersect(start, end, polygon[i], polygon[(i + 1) % polygon.length])) {
        return true;
      }
    }
    return false;
  }

  static boolean segmentsIntersect(double[] a, double[] b, double[] c, double[] d) {
    double d1 = cross(c, d, a);
    double d2 = cross(c, d, b);
    double d3 = cross(a, b, c);
    double d4 = cross(a, b, d);
    if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
        && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
      return true;
    }
    return (Math.abs(d1) <= EPSILON && onSegment(c, d, a))
        || (Math.abs(d2) <= EPSILON && onSegment(c, d, b))
        || (Math.abs(d3) <= EPSILON && onSegment(a, b, c))
        || (Math.abs(d4) <= EPSILON && onSegment(a, b, d));
  }

  static boolean pointInPolygon(double[] point, double[][] polygon) {
    boolean inside = false;
    for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
      double yi = polygon[i][0];
      double xi = polygon[i][1];
      double yj = polygon[j][0];
      double xj = polygon[j][1];
      boolean straddles = (xi > point[1]) != (xj > point[1]);
      if (straddles
          && point[0] < (yj - yi) * (point[1] - xi) / (xj - xi) + yi) {
        inside = !inside;
      }
    }
    return inside;
  }

  private static boolean pointInOrOnPolygon(double[] point, double[][] polygon) {
    if (pointInPolygon(point, polygon)) {
      return true;
    }
    for (int i = 0; i < polygon.length; i++) {
      if (Math.abs(cross(polygon[i], polygon[(i + 1) % polygon.length], point)) <= EPSILON
          && onSegment(polygon[i], polygon[(i + 1) % polygon.length], point)) {
        return true;
      }
    }
    return false;
  }

  private static double cross(double[] origin, double[] a, double[] b) {
    return (a[0] - origin[0]) * (b[1] - origin[1])
        - (a[1] - origin[1]) * (b[0] - origin[0]);
  }

  private static boolean onSegment(double[] a, double[] b, double[] point) {
    return Math.min(a[0], b[0]) - EPSILON <= point[0]
        && point[0] <= Math.max(a[0], b[0]) + EPSILON
        && Math.min(a[1], b[1]) - EPSILON <= point[1]
        && point[1] <= Math.max(a[1], b[1]) + EPSILON;
  }

  private static double[] centroid(double[][] polygon) {
    double lat = 0;
    double lng = 0;
    for (double[] vertex : polygon) {
      lat += vertex[0];
      lng += vertex[1];
    }
    return new double[] {lat / polygon.length, lng / polygon.length};
  }

  private static double[] offsetFromCentroid(
      double[] vertex, double[] centroid, double fraction) {
    return new double[] {
      vertex[0] + (vertex[0] - centroid[0]) * fraction,
      vertex[1] + (vertex[1] - centroid[1]) * fraction
    };
  }

  private static double distance(double[] a, double[] b) {
    double dLat = a[0] - b[0];
    double dLng = a[1] - b[1];
    return Math.sqrt(dLat * dLat + dLng * dLng);
  }
}
