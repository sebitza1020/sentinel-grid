import { Injectable } from '@angular/core';
import { BehaviorSubject, Subject } from 'rxjs';
import { retry } from 'rxjs/operators';
import { webSocket, WebSocketSubject } from 'rxjs/webSocket';
import { TelemetrySnapshot } from '../models/drone.model';

@Injectable({
  providedIn: 'root',
})
export class TelemetryService {
  // BehaviorSubject va ține ultima stare a flotei și va notifica harta când apar schimbări.
  // Contract păstrat: obiect keyed by callSign (aceeași formă emisă înainte de Firebase).
  public dronePositions$ = new BehaviorSubject<TelemetrySnapshot>({});

  // Rute de evaziune calculate de backend (mesaje discriminate cu type: "path").
  public dronePaths$ = new Subject<any>();

  private socket$: WebSocketSubject<any>;

  constructor() {
    const url = this.buildSocketUrl();
    this.socket$ = webSocket<any>(url);

    // retry({ delay }) reconectează automat după cold start-ul Render / drop-uri de rețea.
    this.socket$.pipe(retry({ delay: 3000 })).subscribe({
      next: (data) => {
        if (!data) return;
        // Discriminăm mesajele: rutele au type "path"; snapshot-ul de telemetrie e un
        // map brut keyed by callSign (fără câmp "type") — contractul Reactive Radar rămâne intact.
        if (data.type === 'path') {
          this.dronePaths$.next(data);
        } else {
          this.dronePositions$.next(data);
        }
      },
      error: (err) => console.warn('📡 Telemetry socket error:', err),
    });

    console.log(`📡 Reactive Radar online — streaming telemetry from ${url}`);
  }

  /** ws://localhost:8080 în dev; wss://<backend> în producție (nu există environment files). */
  private buildSocketUrl(): string {
    const host = window.location.hostname;
    const isLocal = host === 'localhost' || host === '127.0.0.1';
    return isLocal
      ? 'ws://localhost:8080/ws/telemetry'
      : 'wss://sentinel-api-kh1p.onrender.com/ws/telemetry';
  }
}
