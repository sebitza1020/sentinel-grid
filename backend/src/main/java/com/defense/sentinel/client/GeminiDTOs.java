package com.defense.sentinel.client;

import java.util.Collections;
import java.util.List;

// Această clasă conține toate structurile mici necesare pentru Request/Response
public class GeminiDTOs {

  // --- REQUEST ---
  public static class Request {
    public List<Content> contents;

    // Constructor helper pentru a crea rapid request-ul
    public Request(String text) {
      this.contents = Collections.singletonList(new Content(text));
    }
  }

  public static class Content {
    public List<Part> parts;

    public Content(String text) {
      this.parts = Collections.singletonList(new Part(text));
    }

    public Content() {
    } // necesar pt jackson
  }

  public static class Part {
    public String text;

    public Part(String text) {
      this.text = text;
    }

    public Part() {
    }
  }

  // --- RESPONSE ---
  public static class Response {
    public List<Candidate> candidates;
  }

  public static class Candidate {
    public Content content;
  }
}