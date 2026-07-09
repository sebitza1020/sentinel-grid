import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

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
  syncGeofences(zones: { id: string; polygon: number[][] }[]): Observable<any> {
    return this.http.post<any>(`${this.base}/api/geofences`, zones);
  }

  /** Cere backend-ului o rută sigură (ocolind zonele) de la start la end (fiecare [lat,lng]). */
  requestRoute(callSign: string, start: number[], end: number[]): Observable<number[][]> {
    return this.http.post<number[][]>(`${this.base}/api/navigation/route`, {
      callSign,
      start,
      end,
    });
  }

  getWeather(): Observable<any> {
    return this.http.get<any>(this.weatherUrl);
  }

  getAll(): Observable<any[]> {
    return this.http.get<any[]>(this.apiUrl);
  }

  create(drone: any): Observable<any> {
    return this.http.post<any>(this.apiUrl, drone);
  }

  delete(id: string): Observable<any> {
    return this.http.delete<any>(`${this.apiUrl}/${id}`);
  }

  sendTelemetry(callSign: string, data: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/${callSign}/ping`, data);
  }
}
