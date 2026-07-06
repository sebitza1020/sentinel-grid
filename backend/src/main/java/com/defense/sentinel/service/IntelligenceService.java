package com.defense.sentinel.service;

import com.defense.sentinel.client.GroqClient;
import com.defense.sentinel.client.GroqRequest;
import com.defense.sentinel.client.GroqResponse;
import com.defense.sentinel.client.OllamaClient;
import com.defense.sentinel.client.OllamaRequest;
import com.defense.sentinel.client.OllamaResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Hybrid threat-detection router. Analyzes a drone field report with either the local Ollama
 * engine (dev) or hosted Groq (prod), selected by the {@code sentinel.ai.engine} config. Both
 * engines share the same strict single-word prompt and {@link #parseVerdict} logic, so the result
 * is always exactly {@code "THREAT"}, {@code "SAFE"}, or {@code "UNKNOWN"}.
 */
@ApplicationScoped
public class IntelligenceService {

  @Inject @RestClient OllamaClient ollamaClient;

  @Inject @RestClient GroqClient groqClient;

  // Ce motor AI folosim: "ollama" (local) sau "groq" (hosted). Default dev = ollama.
  @ConfigProperty(name = "sentinel.ai.engine", defaultValue = "ollama")
  String engine;

  // Modelul local rulat de Ollama (implicit gemma4:12b)
  @ConfigProperty(name = "ollama.model", defaultValue = "gemma4:12b")
  String model;

  // Cheia Groq (din env GROQ_API_KEY) — Optional fiindcă în dev/test e goală (SmallRye
  // tratează string-ul gol drept null).
  @ConfigProperty(name = "groq.api.key")
  Optional<String> groqApiKey;

  @ConfigProperty(name = "groq.model", defaultValue = "llama-3.1-8b-instant")
  String groqModel;

  public String analyzeReport(String reportText) {
    // System prompt strict: cerem EXACT un singur cuvânt, indiferent de motor.
    String instruction =
        "You are a military defense threat-detection system. "
            + "Respond with EXACTLY ONE word and nothing else: SAFE or THREAT.";
    String task = "Analyze this drone field report: '" + reportText + "'.";

    try {
      String raw =
          "groq".equalsIgnoreCase(engine) ? callGroq(instruction, task) : callOllama(instruction, task);
      return parseVerdict(raw);
    } catch (Exception e) {
      System.err.println("❌ AI Uplink Failed (" + engine + "): " + e.getMessage());
      e.printStackTrace();
      return "UNKNOWN";
    }
  }

  /** Local Ollama: promptul e un singur bloc de text. */
  private String callOllama(String instruction, String task) {
    System.out.println("🤖 Sending intel to local Ollama (" + model + ")...");
    String prompt = instruction + " " + task;
    OllamaResponse response = ollamaClient.generate(new OllamaRequest(model, prompt));
    return response != null ? response.response : null;
  }

  /** Hosted Groq: format OpenAI chat cu mesaje system + user și temperatură 0 (determinist). */
  private String callGroq(String instruction, String task) {
    System.out.println("🧠 Sending intel to Groq (" + groqModel + ")...");
    List<GroqRequest.Message> messages =
        List.of(new GroqRequest.Message("system", instruction), new GroqRequest.Message("user", task));
    GroqResponse response =
        groqClient.chat("Bearer " + groqApiKey.orElse(""), new GroqRequest(groqModel, messages, 0.0));

    if (response != null && response.choices != null && !response.choices.isEmpty()) {
      GroqResponse.Choice choice = response.choices.get(0);
      if (choice != null && choice.message != null) {
        return choice.message.content;
      }
    }
    return null;
  }

  /**
   * Shared, robust parsing: modelele pot returna spații/newline sau text suplimentar, așa că
   * normalizăm cu trim().toUpperCase() și căutăm cuvântul-cheie. Fallback: UNKNOWN.
   */
  private String parseVerdict(String raw) {
    if (raw != null) {
      String verdict = raw.trim().toUpperCase();
      if (verdict.contains("THREAT")) {
        return "THREAT";
      }
      if (verdict.contains("SAFE")) {
        return "SAFE";
      }
    }
    return "UNKNOWN";
  }
}
