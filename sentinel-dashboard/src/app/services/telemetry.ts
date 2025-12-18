import { Injectable } from '@angular/core';
import { initializeApp } from 'firebase/app';
import { getDatabase, ref, onValue, connectDatabaseEmulator } from 'firebase/database';
import { BehaviorSubject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class TelemetryService {
  private db: any;
  // BehaviorSubject va ține ultima stare a flotei și va notifica harta când apar schimbări
  public dronePositions$ = new BehaviorSubject<any>({});

  constructor() {
    this.initFirebase();
  }

  private initFirebase() {
    const firebaseConfig = {
      apiKey: "AIzaSyCajXRlD4czRRAiy_6x57zK58xvkcSkju0",
      authDomain: "sentinel-grid-f1119.firebaseapp.com",
      databaseURL: "https://sentinel-grid-f1119-default-rtdb.europe-west1.firebasedatabase.app",
      projectId: "sentinel-grid-f1119",
      storageBucket: "sentinel-grid-f1119.firebasestorage.app",
      messagingSenderId: "842452564408",
      appId: "1:842452564408:web:54e07be9abd0334cfbb74d"
    };

    const app = initializeApp(firebaseConfig);
    this.db = getDatabase(app);

    // CRITIC: Conectăm Angular la Emulatorul Local pornit anterior
    // console.log('📡 Connecting to Firebase Emulator...');
    // connectDatabaseEmulator(this.db, '127.0.0.1', 9000);

    console.log('📡 Connected to LIVE Firebase Sentinel Grid');

    this.listenToDrones();
  }

  private listenToDrones() {
    const dronesRef = ref(this.db, 'live_telemetry');
    
    // Ascultăm în timp real (WebSocket connection deschis de Firebase)
    onValue(dronesRef, (snapshot) => {
      const data = snapshot.val();
      if (data) {
        this.dronePositions$.next(data);
      }
    });
  }
}