package com.defense.sentinel.service;

import com.defense.sentinel.client.GeminiClient;
import com.defense.sentinel.client.GeminiDTOs;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class IntelligenceService {

  @Inject
  @RestClient
  GeminiClient geminiClient;

  @ConfigProperty(name = "gemini.api.key")
  String apiKey;

  public String analyzeReport(String reportText) {
    // System Prompt optimizat pentru Gemini
    String prompt = "You are a military defense system. Analyze this report: '" + reportText +
        "'. Respond with exactly ONE word: SAFE, SUSPICIOUS, or THREAT.";

    try {
      System.out.println("🤖 Sending intel to Gemini Cloud...");

      // Construim request-ul complex
      GeminiDTOs.Request request = new GeminiDTOs.Request(prompt);

      // Apelăm API-ul
      GeminiDTOs.Response response = geminiClient.generate(apiKey, request);

      // Extragem răspunsul din structura nested (Response -> Candidates -> Content ->
      // Parts -> Text)
      if (response.candidates != null && !response.candidates.isEmpty()) {
        String verdict = response.candidates.get(0).content.parts.get(0).text;
        return verdict.trim().toUpperCase().replace("\n", "");
      }

      return "UNKNOWN";
    } catch (Exception e) {
      System.err.println("❌ Gemini Uplink Failed: " + e.getMessage());
      e.printStackTrace();
      return "UNKNOWN";
    }
  }
}