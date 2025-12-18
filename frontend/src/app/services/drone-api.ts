import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class DroneApiService {
  // ⚠️ Înlocuiește cu URL-ul tău de Render
  private apiUrl = 'https://sentinel-api-kh1p.onrender.com/api/drones'; 

  constructor(private http: HttpClient) {}

  getAll(): Observable<any[]> {
    return this.http.get<any[]>(this.apiUrl);
  }

  create(drone: any): Observable<any> {
    return this.http.post<any>(this.apiUrl, drone);
  }

  delete(id: string): Observable<any> {
    return this.http.delete<any>(`${this.apiUrl}/${id}`);
  }
}