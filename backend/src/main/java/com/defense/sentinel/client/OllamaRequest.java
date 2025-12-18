package com.defense.sentinel.client;

// Definim structura cererii către Ollama
public class OllamaRequest {
  public String model;
  public String prompt;
  public boolean stream = false; // Vrem tot răspunsul odată, nu bucată cu bucată

  public OllamaRequest(String model, String prompt) {
    this.model = model;
    this.prompt = prompt;
  }
}
