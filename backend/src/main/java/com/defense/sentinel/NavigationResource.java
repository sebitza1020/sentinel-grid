package com.defense.sentinel;

import com.defense.sentinel.service.NavigationService;
import com.defense.sentinel.websocket.TelemetrySocket;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * Flight planner. Given a drone's current position and its target, returns an evasion route around any
 * active No-Fly Zone and broadcasts it over the WebSocket so every dashboard updates the trajectory.
 */
@Path("/api/navigation")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NavigationResource {

  @Inject NavigationService navigationService;

  @Inject TelemetrySocket telemetrySocket;

  @POST
  @Path("/route")
  public Response route(RouteRequest request) {
    if (request == null || request.start == null || request.end == null) {
      return Response.status(400).build();
    }

    List<double[]> waypoints = navigationService.route(request.start, request.end);

    // Push the planned path to all connected dashboards so trajectories update live.
    telemetrySocket.broadcastPath(request.callSign, waypoints);

    return Response.ok(waypoints).build();
  }
}
