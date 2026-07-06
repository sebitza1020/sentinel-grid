package com.defense.sentinel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.defense.sentinel.client.GroqClient;
import com.defense.sentinel.client.GroqRequest;
import com.defense.sentinel.client.GroqResponse;
import com.defense.sentinel.client.OllamaClient;
import com.defense.sentinel.client.OllamaRequest;
import com.defense.sentinel.client.OllamaResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Plain unit test (no @QuarkusTest) for the hybrid AI router. Hand-rolled fake clients let us
 * exercise both engines, the Groq bearer header, and the strict SAFE/THREAT/UNKNOWN parsing
 * without a running Quarkus context — mirroring {@code WeatherServiceTest}.
 */
class IntelligenceServiceTest {

  /** Returns a canned Ollama verdict, or throws when {@code fail} is set. */
  static class FakeOllamaClient implements OllamaClient {
    String next;
    boolean fail = false;

    @Override
    public OllamaResponse generate(OllamaRequest request) {
      if (fail) {
        throw new RuntimeException("ollama down");
      }
      OllamaResponse r = new OllamaResponse();
      r.response = next;
      return r;
    }
  }

  /** Returns a canned Groq verdict and records the Authorization header it received. */
  static class FakeGroqClient implements GroqClient {
    String next;
    boolean fail = false;
    String seenAuthorization;

    @Override
    public GroqResponse chat(String authorization, GroqRequest request) {
      this.seenAuthorization = authorization;
      if (fail) {
        throw new RuntimeException("groq down");
      }
      GroqResponse.Message m = new GroqResponse.Message();
      m.role = "assistant";
      m.content = next;
      GroqResponse.Choice c = new GroqResponse.Choice();
      c.message = m;
      GroqResponse r = new GroqResponse();
      r.choices = List.of(c);
      return r;
    }
  }

  private IntelligenceService newService(FakeOllamaClient ollama, FakeGroqClient groq, String engine) {
    IntelligenceService service = new IntelligenceService();
    service.ollamaClient = ollama;
    service.groqClient = groq;
    service.engine = engine;
    service.model = "gemma4:12b";
    service.groqApiKey = Optional.of("test-key");
    service.groqModel = "llama-3.1-8b-instant";
    return service;
  }

  @Test
  void routesToOllamaAndParsesThreat() {
    FakeOllamaClient ollama = new FakeOllamaClient();
    ollama.next = "  threat\n"; // messy output should still normalize
    FakeGroqClient groq = new FakeGroqClient();

    IntelligenceService service = newService(ollama, groq, "ollama");

    assertEquals("THREAT", service.analyzeReport("Armed convoy spotted"));
    assertEquals(null, groq.seenAuthorization, "groq must not be called when engine=ollama");
  }

  @Test
  void routesToGroqAndParsesSafe() {
    FakeOllamaClient ollama = new FakeOllamaClient();
    FakeGroqClient groq = new FakeGroqClient();
    groq.next = "SAFE";

    IntelligenceService service = newService(ollama, groq, "groq");

    assertEquals("SAFE", service.analyzeReport("Sector clear"));
    assertEquals("Bearer test-key", groq.seenAuthorization, "groq must receive the bearer token");
  }

  @Test
  void returnsUnknownForUnparseableOutput() {
    FakeOllamaClient ollama = new FakeOllamaClient();
    ollama.next = "I'm not sure about that";
    FakeGroqClient groq = new FakeGroqClient();

    IntelligenceService service = newService(ollama, groq, "ollama");

    assertEquals("UNKNOWN", service.analyzeReport("ambiguous"));
  }

  @Test
  void returnsUnknownWhenEngineThrows() {
    FakeOllamaClient ollama = new FakeOllamaClient();
    FakeGroqClient groq = new FakeGroqClient();
    groq.fail = true;

    IntelligenceService service = newService(ollama, groq, "groq");

    assertEquals("UNKNOWN", service.analyzeReport("Armed convoy"));
  }
}
