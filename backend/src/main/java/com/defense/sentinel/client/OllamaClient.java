package com.defense.sentinel.client;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

// URL configurabilă prin env (OLLAMA_API_URL), cu default localhost pentru dev
@RegisterRestClient(configKey = "ollama-api")
public interface OllamaClient {

  @POST
  @Path("/generate")
  OllamaResponse generate(OllamaRequest request);
}