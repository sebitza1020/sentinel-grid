package com.defense.sentinel.client;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

// Interfața clientului
@RegisterRestClient(configKey = "google-alert-api")
public interface WebhookClient {
  @POST
  Response sendAlert(AlertPayload payload);
}