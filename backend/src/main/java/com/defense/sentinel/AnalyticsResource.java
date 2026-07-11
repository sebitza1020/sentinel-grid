package com.defense.sentinel;

import com.defense.sentinel.service.MissionDebriefService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

/**
 * Analytics exports. Serves the official tactical debrief as a downloadable PDF, assembled from the
 * live fleet pool, cached weather, and the session AI verdict log.
 */
@Path("/api/analytics")
public class AnalyticsResource {

  @Inject MissionDebriefService missionDebriefService;

  @GET
  @Path("/export")
  @Produces("application/pdf")
  public Response export() {
    byte[] pdf = missionDebriefService.generateDebriefPdf();
    return Response.ok(pdf)
        .type("application/pdf")
        .header("Content-Disposition", "attachment; filename=sentinel-debrief.pdf")
        .build();
  }
}
