package com.defense.sentinel;

import com.defense.sentinel.service.GeofenceService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * No-Fly Zone registry endpoint. The operator draws polygons on the tactical map and syncs the full
 * active set here; the navigation planner reads it to route drones around restricted airspace.
 */
@Path("/api/geofences")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GeofenceResource {

  @Inject GeofenceService geofenceService;

  @GET
  public List<Geofence> list() {
    return geofenceService.getZones();
  }

  @POST
  public Response sync(List<Geofence> zones) {
    geofenceService.replaceAll(zones);
    return Response.ok(geofenceService.getZones()).build();
  }
}
