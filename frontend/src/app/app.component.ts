import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DroneApiService } from './services/drone-api.service';
import { TelemetryService } from './services/telemetry.service';
import * as L from 'leaflet';
import { AuthService } from './services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {
  private map: any;
  private markers: { [key: string]: L.CircleMarker } = {};

  // UI State
  showAdmin = false;
  isLoading = false;
  
  // Data State
  dbDrones: any[] = [];
  newDrone: any = { callSign: '', model: '' }; // Model simplificat pentru formular

  // Atmospheric Sensors (Open-Meteo -> backend /api/weather)
  weatherData: any = null;

  // Dicționar pentru a ține minte care dronă zboară (ID -> Interval Timer)
  simulationIntervals: { [key: string]: any } = {};

  loginData = { username: '', password: '' };

  selectedDroneId: string | null = null;
  flightVectors: { [key: string]: { targetLat: number, targetLng: number } } = {};
  flightPaths: { [key: string]: L.Polyline } = {};

  constructor(
    private telemetryService: TelemetryService,
    private apiService: DroneApiService,
    public authService: AuthService
  ) { }
  
  doLogin() {
    this.authService.login(this.loginData.username, this.loginData.password).subscribe({
      next: () => {
        alert('ACCESS GRANTED. Welcome back, Commander.');
        this.loadDrones(); // Încărcăm datele după login
      },
      error: () => alert('ACCESS DENIED. Invalid credentials.')
    });
  }

  doLogout() {
    this.authService.logout();
    this.dbDrones = []; // Golim lista vizual
  }

  ngOnInit() {
    this.initMap();
    this.setupTelemetry();
    this.loadDrones(); // Încercăm încărcarea la start
    this.loadWeather(); // Citim senzorii atmosferici
  }

  // --- ATMOSPHERIC SENSORS LOGIC ---
  private loadWeather() {
    this.apiService.getWeather().subscribe({
      next: (data) => {
        this.weatherData = data;
        console.log('🌡️ Atmospheric sensors online:', data);
      },
      error: (err) => {
        console.warn('⚠️ Atmospheric sensors offline:', err.status);
      }
    });
  }

  // --- MAP LOGIC ---
  private initMap(): void {
    this.map = L.map('map').setView([44.4268, 26.1025], 13);
    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
      attribution: '© OpenStreetMap & CartoDB',
      maxZoom: 19
    }).addTo(this.map);

    // --- ASCULTĂ CLICK-URILE PE HARTĂ ---
    this.map.on('click', (e: any) => {
      if (this.selectedDroneId) {
        this.setWayPoint(this.selectedDroneId, e.latlng.lat, e.latlng.lng);
      }
    });
  }

  setWayPoint(droneId: string, lat: number, lng: number) {
    console.log(`📍 New Waypoint for ${droneId}: [${lat}, ${lng}]`);
    
    // 1. Salvăm ținta
    this.flightVectors[droneId] = { targetLat: lat, targetLng: lng };

    // 2. Desenăm linia pe hartă (Feedback Vizual)
    // Mai întâi ștergem linia veche dacă există
    if (this.flightPaths[droneId]) {
      this.flightPaths[droneId].remove();
    }

    // Găsim poziția curentă a dronei (din marker)
    const currentPos = this.markers[droneId] ? this.markers[droneId].getLatLng() : this.map.getCenter();

    // Desenăm linia punctată spre țintă
    this.flightPaths[droneId] = L.polyline([currentPos, [lat, lng]], {
      color: '#00f3ff',
      weight: 2,
      dashArray: '5, 10', // Linie punctată
      opacity: 0.7
    }).addTo(this.map);
  }

  private setupTelemetry() {
    this.telemetryService.dronePositions$.subscribe(data => {
      this.updateMarkers(data);
    });
  }

  private updateMarkers(drones: any) {
    Object.keys(drones).forEach(key => {
      const d = drones[key];
      const isThreat = d.threat_level === 'THREAT';
      const color = isThreat ? '#ff003c' : '#00f3ff';
      
      // --- LOGICA NOUĂ PENTRU IMAGINE ---
      let imageHtml = '';
      if (isThreat && d.report) {
        // 1. Extragem partea esențială din raport (scoatem "Visual contact:")
        // Ex: "Visual contact: Armed convoy" devine "Armed convoy"
        let cleanPrompt = d.report.replace('Visual contact:', '').trim();
        
        // 2. Adăugăm "condimente" pentru a arăta a imagine de spionaj militar
        cleanPrompt += ' drone surveillance view night vision grainy';

        // 3. Codificăm URL-ul (ca să meargă spațiile etc.)
        const encodedPrompt = encodeURIComponent(cleanPrompt);
        // Folosim serviciul Pollinations.ai (gratuit, prin URL)
        const imageUrl = `https://image.pollinations.ai/prompt/${encodedPrompt}?width=300&height=200&nologo=true`;
        
        // 4. Construim HTML-ul imaginii
        imageHtml = `
          <div style="margin-top: 10px; position: relative;">
            <img src="${imageUrl}" alt="Recon Image" 
                  style="width: 100%; border-radius: 4px; border: 2px solid ${color}; min-height: 150px; background: #222;">
            <div style="position: absolute; top: 5px; left: 5px; background: rgba(0,0,0,0.7); color: ${color}; font-size: 0.7em; padding: 2px 5px;">
                AI RECON SIMULATION
            </div>
          </div>
        `;
      }
      // ----------------------------------

      // Construim conținutul popup-ului (cu tot cu imagine, dacă există)
      const popupContent = `
          <div style="text-align: center; min-width: 200px;">
            <b style="color: ${color}; font-size: 1.2em; letter-spacing: 1px;">${key.toUpperCase()}</b><br>
            Status: <b style="color: ${color}">${d.threat_level || 'ANALYZING...'}</b><br>
            
            ${imageHtml} <hr style="border-color: rgba(255,255,255,0.2); margin: 10px 0;">
            <small style="color: #ccc; font-style: italic;">"${d.report || 'No report'}"</small>
          </div>
        `;

      if (this.markers[key]) {
        // CAZUL 1: Drona există - Update
        this.markers[key].setLatLng([d.lat, d.lng]);
        this.markers[key].setStyle({ color: color, fillColor: color });
        // Doar actualizăm conținutul popup-ului dacă e deschis
        if (this.markers[key].isPopupOpen()) {
          this.markers[key].getPopup()?.setContent(popupContent);
        } else {
          // Altfel îl setăm pentru când va fi deschis
          this.markers[key].setPopupContent(popupContent);
        }
      } else {
        // CAZUL 2: Drona nouă - Creare
        const marker = L.circleMarker([d.lat, d.lng], {
          color: color,
          fillColor: color,
          fillOpacity: 0.7,
          radius: 12,
          weight: 3
        }).addTo(this.map);

        marker.bindPopup(popupContent);
        this.markers[key] = marker;
      }
    });
  }

  // --- FLEET MANAGEMENT LOGIC ---
  toggleAdmin() {
    this.showAdmin = !this.showAdmin;
    if (this.showAdmin) {
      this.loadDrones();
    }
  }

  // --- SIMULATION LOGIC ---
  toggleSimulation(drone: any) {
    if (this.simulationIntervals[drone.id]) {
      this.stopSimulation(drone);
    } else {
      this.startSimulation(drone);
    }
  }

  startSimulation(drone: any) {
    console.log(`🚀 Manual Control Active for ${drone.callSign}`);
    
    // O selectăm automat când apăsăm Play
    this.selectedDroneId = drone.id;
    
    // Poziția inițială (dacă nu are, pornește din centru)
    let currentLat = 44.4268;
    let currentLng = 26.1025;

    // Dacă drona e deja pe hartă, plecăm de acolo
    if (this.markers[drone.id]) {
      const pos = this.markers[drone.id].getLatLng();
      currentLat = pos.lat;
      currentLng = pos.lng;
    }

    // Intervalul de simulare (15 secunde pentru Gemini)
    this.simulationIntervals[drone.id] = setInterval(() => {
      
      // VERIFICĂM DACĂ AVEM O ȚINTĂ (Waypoint)
      const vector = this.flightVectors[drone.id];

      if (vector) {
        // --- LOGICA DE MIȘCARE SPRE ȚINTĂ ---
        // Calculăm distanța rămasă
        const distLat = vector.targetLat - currentLat;
        const distLng = vector.targetLng - currentLng;
        
        // Viteza de deplasare (aproximativ 20% din distanță per "tick" sau un pas fix)
        // Pentru demo, facem pași mai mari să se vadă mișcarea
        const step = 0.002; 
        
        // Dacă suntem foarte aproape, ne oprim
        if (Math.abs(distLat) < 0.0005 && Math.abs(distLng) < 0.0005) {
          console.log('Target reached. Holding position.');
          // Putem șterge linia
          if (this.flightPaths[drone.id]) this.flightPaths[drone.id].remove();
        } else {
          // Ne mișcăm spre țintă
          // Normalizăm vectorul (direcția)
          const angle = Math.atan2(distLat, distLng);
          currentLat += Math.sin(angle) * step;
          currentLng += Math.cos(angle) * step;
          
          // Actualizăm linia vizuală (să plece din noua poziție)
          if (this.flightPaths[drone.id]) {
            this.flightPaths[drone.id].setLatLngs([[currentLat, currentLng], [vector.targetLat, vector.targetLng]]);
          }
        }

      } else {
        // --- LOGICA VECHE (CERC) - FALLBACK DACĂ NU DAI CLICK ---
        // (Opțional: Poți lăsa drona să stea pe loc dacă nu are țintă)
        currentLat += (Math.random() - 0.5) * 0.001;
        currentLng += (Math.random() - 0.5) * 0.001;
      }

      // --- SCENARIU THREAT & AI --- (Rămâne neschimbat)
      const isThreat = Math.random() > 0.8;
      const reportText = isThreat 
        ? "Visual contact: Armed convoy moving towards civilian sector." 
        : "Sector clear. En route to waypoint.";

      const payload = {
        lat: currentLat,
        lng: currentLng,
        alt: 150 + Math.random() * 50,
        battery: Math.floor(Math.random() * 100),
        report: reportText
      };

      this.apiService.sendTelemetry(drone.callSign, payload).subscribe({
        next: () => console.log(`📡 Telemetry sent`),
        error: (err) => console.warn('Telemetry skipped:', err.status)
      });

    }, 15000); // 15 secunde
  }

  stopSimulation(drone: any) {
    console.log(`🛑 Stopping simulation for ${drone.callSign}`);
    if (this.simulationIntervals[drone.id]) {
      clearInterval(this.simulationIntervals[drone.id]);
      delete this.simulationIntervals[drone.id];
    }
  }

  // Helper pentru UI: verifică dacă o dronă e în zbor
  isSimulating(id: string): boolean {
    return !!this.simulationIntervals[id];
  }

  loadDrones() {
    this.isLoading = true;
    this.apiService.getAll().subscribe({
      next: (data) => {
        this.dbDrones = data;
        this.isLoading = false;
        console.log('✅ Drones loaded from SQL:', data);
      },
      error: (err) => {
        console.error('❌ Failed to load drones:', err);
        this.isLoading = false;
        // Nu dăm alert la load automat, ca să nu stresăm userul dacă backend-ul doarme
      }
    });
  }

  addDrone() {
    if (!this.newDrone.callSign || !this.newDrone.model) return;

    this.isLoading = true;
    const payload = {
      callSign: this.newDrone.callSign,
      model: this.newDrone.model,
      batteryCapacity: 10000, // Default militar
      status: 'OFFLINE'
    };

    this.apiService.create(payload).subscribe({
      next: (resp) => {
        this.dbDrones.push(resp);

        this.newDrone = { callSign: '', model: '' }; 
        this.isLoading = false;
        
        console.log(`Unit ${resp.callSign} deployed successfully!`);
      },
      error: (err) => {
        console.error(err);
        alert(`DEPLOY FAILED: ${err.message}`);
        this.isLoading = false;
      }
    });
  }

  decommission(id: string) {
    if (confirm('⚠️ WARNING: Confirm decommissioning of this unit?')) {
      
      const backupList = [...this.dbDrones];
      this.dbDrones = this.dbDrones.filter(d => d.id !== id);

      this.apiService.delete(id).subscribe({
        next: () => {
          console.log('Unit decommissioned confirmed by HQ.');
        },
        error: (err) => {
          alert('Decommission failed: ' + err.message);
          this.dbDrones = backupList;
        }
      });
    }
  }
}