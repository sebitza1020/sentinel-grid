import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, tap } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AuthService {
  // URL-ul backend-ului tău
  private apiUrl = 'https://sentinel-api-kh1p.onrender.com/api/auth/login';
  
  // Ținem minte token-ul în memorie și localStorage
  private tokenKey = 'sentinel_token';
  isLoggedIn$ = new BehaviorSubject<boolean>(!!localStorage.getItem(this.tokenKey));

  constructor(private http: HttpClient) {}

  login(username: string, password: string) {
    return this.http.post<{token: string}>(this.apiUrl, { username, password }).pipe(
      tap(response => {
        // Când primim token-ul, îl salvăm
        localStorage.setItem(this.tokenKey, response.token);
        this.isLoggedIn$.next(true);
      })
    );
  }

  logout() {
    localStorage.removeItem(this.tokenKey);
    this.isLoggedIn$.next(false);
  }

  getToken() {
    return localStorage.getItem(this.tokenKey);
  }
}