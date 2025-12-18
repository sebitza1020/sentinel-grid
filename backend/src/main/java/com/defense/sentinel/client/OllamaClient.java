package com.defense.sentinel.client;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(baseUri = "http://localhost:11434/api")
public interface OllamaClient {

  @POST
  @Path("/generate")
  OllamaResponse generate(OllamaRequest request);
}