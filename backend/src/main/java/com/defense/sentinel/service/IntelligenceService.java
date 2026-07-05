package com.defense.sentinel.service;

import com.defense.sentinel.client.OllamaClient;
import com.defense.sentinel.client.OllamaRequest;
import com.defense.sentinel.client.OllamaResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class IntelligenceService {

  @Inject
  @RestClient
  OllamaClient ollamaClient;

  // Modelul local rulat de Ollama (implicit gemma4:12b)
  @ConfigProperty(name = "ollama.model", defaultValue = "gemma4:12b")
  String model;

  public String analyzeReport(String reportText) {
    // System Prompt optimizat pentru modelul local. Cerem STRICT un singur cuvânt.
    String prompt = "You are a military defense threat-detection system. " +
        "Analyze this drone field report: '" + reportText + "'. " +
        "Respond with EXACTLY ONE word and nothing else: SAFE or THREAT.";

    try {
      System.out.println("🤖 Sending intel to local Ollama (" + model + ")...");

      // Apelăm instanța locală de Ollama
      OllamaResponse response = ollamaClient.generate(new OllamaRequest(model, prompt));

      // Ollama poate returna spații/newline sau text suplimentar - parsăm robust
      if (response != null && response.response != null) {
        String verdict = response.response.trim().toUpperCase();

        if (verdict.contains("THREAT")) {
          return "THREAT";
        }
        if (verdict.contains("SAFE")) {
          return "SAFE";
        }
      }

      return "UNKNOWN";
    } catch (Exception e) {
      System.err.println("❌ Ollama Uplink Failed: " + e.getMessage());
      e.printStackTrace();
      return "UNKNOWN";
    }
  }
}
