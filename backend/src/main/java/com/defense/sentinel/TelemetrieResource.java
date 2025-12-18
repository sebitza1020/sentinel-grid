package com.defense.sentinel;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.defense.sentinel.client.AlertPayload;
import com.defense.sentinel.client.WebhookClient;
import com.defense.sentinel.service.FirebaseService;
import com.defense.sentinel.service.IntelligenceService;

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

    @GET
    public List<Drone> listAll() {
        return Drone.listAll();
    }

    @POST
    @Transactional
    public Response enlistDrone(Drone drone) {
        if (drone.callSign == null) {
            return Response.status(400).entity("Call Sign is mandatory").build();
        }

        drone.status = "OFFLINE";
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
        update.put("last_seen", System.currentTimeMillis());

        // LOGICA AI: Analizăm doar dacă avem un raport nou
        if (data.report != null && !data.report.isEmpty()) {
            System.out.println("🔍 Analyzing report for " + callSign + ": " + data.report);

            // Call sincron către AI (în producție l-am face asincron/job queue)
            String threatLevel = intelligenceService.analyzeReport(data.report);
            update.put("threat_level", threatLevel);
            update.put("last_report", data.report);

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
                        e.printStackTrace();
                    }
                }).start();
            }

            System.out.println("⚠️ AI Verdict: " + threatLevel);
        }

        firebaseService.getTelemetryRef().child(callSign).updateChildrenAsync(update);

        return Response.ok().build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response updateDrone(@PathParam("id") UUID id, Drone droneUpdate) {
        Drone entity = Drone.findById(id);
        if (entity == null) {
            return Response.status(404).build();
        }

        // Actualizăm câmpurile permise
        if (droneUpdate.model != null)
            entity.model = droneUpdate.model;
        if (droneUpdate.status != null)
            entity.status = droneUpdate.status;
        if (droneUpdate.callSign != null)
            entity.callSign = droneUpdate.callSign;

        return Response.ok(entity).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
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