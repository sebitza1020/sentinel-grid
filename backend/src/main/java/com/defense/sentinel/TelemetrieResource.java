package com.defense.sentinel;

import com.defense.sentinel.client.AlertPayload;
import com.defense.sentinel.client.WebhookClient;
import com.defense.sentinel.service.FirebaseService;
import com.defense.sentinel.service.FleetCommanderService;
import com.defense.sentinel.service.IntelligenceService;
import com.defense.sentinel.service.MissionDebriefService;
import com.defense.sentinel.service.RtbService;
import com.defense.sentinel.websocket.TelemetrySocket;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Path("/api/drones")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TelemetrieResource {

    @Inject
    FirebaseService firebaseService;

    @Inject
    IntelligenceService intelligenceService;

    @Inject
    @RestClient
    WebhookClient webhookClient;

    @Inject
    TelemetrySocket telemetrySocket;

    @Inject
    FleetCommanderService fleetCommanderService;

    @Inject
    MissionDebriefService missionDebriefService;

    @Inject
    RtbService rtbService;

    @GET
    public List<Drone> listAll() {
        return Drone.listAll();
    }

    @POST
    @Transactional
    public Response enlistDrone(@Valid DroneCreateRequest request) {
        Drone drone = Drone.create(request);
        drone.persist();

        return Response.status(201).entity(drone).build();
    }

    @POST
    @Path("/{callSign}/ping")
    public Response updateTelemetry(@PathParam("callSign") String callSign, TelemetryData data) {
        if (data == null)
            return Response.status(400).build();

        Map<String, Object> update = new HashMap<>();
        update.put("lat", data.lat);
        update.put("lng", data.lng);
        update.put("alt", data.alt);
        update.put("batt", data.battery);
        // Frontend-ul (harta + Black Box) citește "battery"; îl publicăm explicit.
        update.put("battery", data.battery);
        update.put("last_seen", System.currentTimeMillis());

        // LOGICA AI: Analizăm doar dacă avem un raport nou
        if (data.report != null && !data.report.isEmpty()) {
            System.out.println("🔍 Analyzing report for " + callSign + ": " + data.report);

            // Call sincron către AI (în producție l-am face asincron/job queue)
            String threatLevel = intelligenceService.analyzeReport(data.report);
            update.put("threat_level", threatLevel);
            // Frontend-ul (popup + imaginea recon + Black Box) citește "report"; înainte
            // scriam "last_report", așa că raportul apărea mereu ca "No report".
            update.put("report", data.report);

            // Mission Debrief: consemnăm verdictul în jurnalul cronologic al sesiunii.
            missionDebriefService.recordVerdict(callSign, threatLevel, data.report);

            // LOGICA NOUĂ DE ALERTĂ
            if ("THREAT".equals(threatLevel)) {
                System.out.println("🚨 CRITICAL: Sending alert to Command Center via Google!");

                // Trimitem notificarea asincron (fire and forget) ca să nu blocăm drona
                // Într-un proiect real am folosi @Asynchronous sau un Event Bus,
                // dar aici facem un thread simplu pentru demo.
                new Thread(() -> {
                    try {
                        webhookClient.sendAlert(new AlertPayload(callSign, data.report, data.lat, data.lng));
                    } catch (Exception e) {
                        // Best-effort alert: the THREAT verdict is still written to Firebase even if
                        // the webhook is unreachable (e.g. 403 when the Apps Script isn't public).
                        System.err.println("⚠️ Alert webhook delivery failed for " + callSign + " ("
                                + e.getMessage() + "). Threat is recorded; alert email was not sent.");
                    }
                }).start();

                // AI Fleet Commander: autonomously re-task the closest available unit to reinforce.
                fleetCommanderService.reinforce(callSign, data.lat, data.lng, data.alt);
            }

            System.out.println("⚠️ AI Verdict: " + threatLevel);
        }

        // Autonomous RTB: decay the battery and, if critical, override the waypoint to the nearest
        // base. Mutates `update` (battery + status/target) before it is persisted and broadcast.
        rtbService.apply(callSign, data.lat, data.lng, update);

        firebaseService.getTelemetryRef().child(callSign).updateChildrenAsync(update);

        // Reactive Radar: push live state to all connected dashboards over the WebSocket.
        telemetrySocket.updateAndBroadcast(callSign, update);

        return Response.ok().build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    @RolesAllowed("COMMANDER")
    public Response updateDrone(@PathParam("id") UUID id, @Valid DroneUpdateRequest droneUpdate) {
        Drone entity = Drone.findById(id);
        if (entity == null) {
            return Response.status(404).build();
        }

        entity.apply(droneUpdate);

        return Response.ok(entity).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @RolesAllowed("COMMANDER")
    public Response deleteDrone(@PathParam("id") UUID id) {
        boolean deleted = Drone.deleteById(id);
        if (!deleted) {
            return Response.status(404).build();
        }
        // Opțional: Aici ai putea șterge și din Firebase, dar lăsăm asta pentru
        // simplitate
        return Response.noContent().build();
    }
}
