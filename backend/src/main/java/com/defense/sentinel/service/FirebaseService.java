package com.defense.sentinel.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.FileInputStream;
import java.io.IOException;

@ApplicationScoped
@Startup
public class FirebaseService {

  @ConfigProperty(name = "firebase.database.url")
  String databaseUrl;

  @ConfigProperty(name = "firebase.emulator.host", defaultValue = "null")
  String emulatorHost;

  @PostConstruct
  public void initialize() {
      try {
          FirebaseOptions.Builder optionsBuilder = new FirebaseOptions.Builder()
                  .setDatabaseUrl(databaseUrl);

          // LOGICA HIBRIDĂ: Emulator vs Prod
          if (emulatorHost != null && !emulatorHost.equals("null") && !emulatorHost.isEmpty()) {
              // --- DEV (Emulator) ---
              optionsBuilder.setCredentials(GoogleCredentials.create(null));
              System.out.println("⚠️ SENTINEL: Conectare la Firebase EMULATOR pe " + emulatorHost);
          } else {
              // --- PROD (Render) ---
              System.out.println("⚠️ SENTINEL: Configurat pentru PROD. Căutăm cheia secretă...");

              // Citim calea către fișierul secret din variabila de mediu standard Google
              String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");

              if (credentialsPath != null && !credentialsPath.isEmpty()) {
                  // Încărcăm fișierul JSON real
                  try (FileInputStream serviceAccount = new FileInputStream(credentialsPath)) {
                      optionsBuilder.setCredentials(GoogleCredentials.fromStream(serviceAccount));
                      System.out.println("✅ SENTINEL: Cheia secretă încărcată cu succes din: " + credentialsPath);
                  }
              } else {
                  // Fail-safe: Dacă uităm variabila, încercăm calea default Render
                  System.out.println("⚠️ Variabila GOOGLE_APPLICATION_CREDENTIALS lipsește. Încercăm /etc/secrets/firebase-key.json");
                  try (FileInputStream serviceAccount = new FileInputStream("/etc/secrets/firebase-key.json")) {
                      optionsBuilder.setCredentials(GoogleCredentials.fromStream(serviceAccount));
                  }
              }
          }

          if (FirebaseApp.getApps().isEmpty()) {
              FirebaseApp.initializeApp(optionsBuilder.build());
          }
      } catch (IOException e) {
          // Aici vrem să crape aplicația dacă nu avem securitate, ca să nu pornească nesecurizat
          throw new RuntimeException("❌ EROARE CRITICĂ FIREBASE: Nu am putut citi fișierul de credențiale!", e);
      }
  }

  public DatabaseReference getTelemetryRef() {
    return FirebaseDatabase.getInstance().getReference("live_telemetry");
  }
}