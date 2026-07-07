package com.defense.sentinel.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// Răspunsul Groq/OpenAI conține multe câmpuri (id, usage, created...) — ignorăm
// tot ce nu ne interesează și extragem doar choices[0].message.content.
@JsonIgnoreProperties(ignoreUnknown = true)
public class GroqResponse {
  public List<Choice> choices;

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Choice {
    public Message message;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Message {
    public String role;
    public String content;
  }
}
