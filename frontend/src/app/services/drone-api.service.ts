import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Drone,
  DroneCreateRequest,
  GeofenceVolume,
  Position3D,
  VoiceCommandResponse,
} from '../models/drone.model';

@Injectable({ providedIn: 'root' })
export class DroneApiService {
  private apiUrl = 'https://sentinel-api-kh1p.onrender.com/api/drones';
  private weatherUrl = 'https://sentinel-api-kh1p.onrender.com/api/weather';
  // Bază derivată la runtime pentru endpoint-urile noi (localhost în dev, Render în prod).
  private base = this.resolveBase();

  constructor(private http: HttpClient) {}

  private resolveBase(): string {
    const host = window.location.hostname;
    const isLocal = host === 'localhost' || host === '127.0.0.1';
    return isLocal ? 'http://localhost:8080' : 'https://sentinel-api-kh1p.onrender.com';
  }

  /** Sincronizează setul complet de No-Fly Zones (poligoane) la backend. */
  getGeofences(): Observable<GeofenceVolume[]> {
    return this.http.get<GeofenceVolume[]>(`${this.base}/api/geofences`);
  }

  syncGeofences(zones: GeofenceVolume[]): Observable<GeofenceVolume[]> {
    return this.http.post<GeofenceVolume[]>(`${this.base}/api/geofences`, zones);
  }

  /** Cere backend-ului o rută sigură (ocolind zonele) de la start la end (fiecare [lat,lng]). */
  requestRoute(callSign: string, start: Position3D, end: Position3D): Observable<Position3D[]> {
    return this.http.post<Position3D[]>(`${this.base}/api/navigation/route`, {
      callSign,
      start,
      end,
    });
  }

  commandVoice(transcript: string): Observable<VoiceCommandResponse> {
    return this.http.post<VoiceCommandResponse>(
      `${this.base}/api/fleet/command-voice`,
      transcript,
      { headers: new HttpHeaders({ 'Content-Type': 'text/plain' }) },
    );
  }

  /** Descarcă debrief-ul tactic oficial (PDF) generat de backend, ca blob binar. */
  exportDebriefPdf(): Observable<Blob> {
    return this.http.get(`${this.base}/api/analytics/export`, { responseType: 'blob' });
  }

  getWeather(): Observable<any> {
    return this.http.get<any>(this.weatherUrl);
  }

  getAll(): Observable<Drone[]> {
    return this.http.get<Drone[]>(this.apiUrl);
  }

  create(drone: DroneCreateRequest): Observable<Drone> {
    return this.http.post<Drone>(this.apiUrl, drone);
  }

  delete(id: string): Observable<any> {
    return this.http.delete<any>(`${this.apiUrl}/${id}`);
  }

  sendTelemetry(callSign: string, data: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/${callSign}/ping`, data);
  }
}
