package com.defense.sentinel.client;

import java.util.List;

// Structura cererii în formatul standard OpenAI/Groq chat-completions.
public class GroqRequest {
  public String model;
  public List<Message> messages;
  public double temperature;

  public GroqRequest(String model, List<Message> messages, double temperature) {
    this.model = model;
    this.messages = messages;
    this.temperature = temperature;
  }

  // Un mesaj din conversație: rol ("system"/"user") + conținut.
  public static class Message {
    public String role;
    public String content;

    public Message(String role, String content) {
      this.role = role;
      this.content = content;
    }
  }
}
