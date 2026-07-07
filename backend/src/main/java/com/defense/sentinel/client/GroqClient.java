package com.defense.sentinel.client;

import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

// Groq expune un API compatibil OpenAI. URL-ul vine din config (GROQ_API_URL),
// iar token-ul Bearer e pasat de serviciu ca header, ca să ținem clientul stateless.
@RegisterRestClient(configKey = "groq-api")
public interface GroqClient {

  @POST
  @Path("/chat/completions")
  GroqResponse chat(@HeaderParam("Authorization") String authorization, GroqRequest request);
}
