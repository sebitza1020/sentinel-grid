package com.defense.sentinel.client;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

// Definim clientul care trage spre Google
@RegisterRestClient(baseUri = "https://generativelanguage.googleapis.com/v1beta")
public interface GeminiClient {

  @POST
  @Path("/models/gemini-2.5-flash-lite:generateContent")
  GeminiDTOs.Response generate(@QueryParam("key") String apiKey, GeminiDTOs.Request request);
}