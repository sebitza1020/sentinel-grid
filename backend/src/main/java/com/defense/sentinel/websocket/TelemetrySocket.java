package com.defense.sentinel.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Real-time telemetry stream. The frontend connects here (RxJS webSocket) instead of polling; every
 * drone update pushed via {@link #updateAndBroadcast} is fanned out to all connected clients as a
 * JSON object keyed by call sign — the same shape the old Firebase read path emitted, so the map and
 * Black Box analytics react instantly with no client changes.
 */
@ServerEndpoint("/ws/telemetry")
@ApplicationScoped
public class TelemetrySocket {

  // Sesiunile deschise (thread-safe: broadcast rulează pe thread-uri diferite de open/close).
  final Set<Session> sessions = new CopyOnWriteArraySet<>();

  // Starea curentă a fiecărei drone, keyed by callSign. Facem MERGE (nu replace) ca un ping doar
  // de mișcare să nu șteargă un threat_level setat anterior de AI.
  final Map<String, Map<String, Object>> droneStates = new ConcurrentHashMap<>();

  @Inject ObjectMapper objectMapper;

  @OnOpen
  public void onOpen(Session session) {
    sessions.add(session);
    // Trimitem imediat starea curentă, ca un client nou să vadă flota fără să aștepte un ping.
    sendTo(session, snapshotJson());
  }

  @OnClose
  public void onClose(Session session) {
    sessions.remove(session);
  }

  @OnError
  public void onError(Session session, Throwable throwable) {
    sessions.remove(session);
  }

  @OnMessage
  public void onMessage(String message) {
    // Clientul e read-only; ignorăm eventualele mesaje primite.
  }

  /** Actualizează (merge) starea unei drone și difuzează întregul snapshot tuturor sesiunilor. */
  public void updateAndBroadcast(String callSign, Map<String, Object> delta) {
    droneStates.merge(callSign, new HashMap<>(delta), (existing, incoming) -> {
      existing.putAll(incoming);
      return existing;
    });
    broadcast();
  }

  /** Difuzează snapshot-ul curent al flotei către toate sesiunile deschise. */
  public void broadcast() {
    String json = snapshotJson();
    for (Session session : sessions) {
      sendTo(session, json);
    }
  }

  private void sendTo(Session session, String json) {
    if (json != null && session.isOpen()) {
      session.getAsyncRemote().sendText(json);
    }
  }

  String snapshotJson() {
    try {
      return objectMapper.writeValueAsString(droneStates);
    } catch (Exception e) {
      System.err.println("⚠️ Telemetry broadcast serialization failed: " + e.getMessage());
      return null;
    }
  }
}
