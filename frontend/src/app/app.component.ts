import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DroneApiService } from './services/drone-api.service';
import { TelemetryService } from './services/telemetry.service';
import * as L from 'leaflet';

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

  // Dicționar pentru a ține minte care dronă zboară (ID -> Interval Timer)
  simulationIntervals: { [key: string]: any } = {};

  constructor(
    private telemetryService: TelemetryService,
    private apiService: DroneApiService
  ) {}

  ngOnInit() {
    this.initMap();
    this.setupTelemetry();
    this.loadDrones(); // Încercăm încărcarea la start
  }

  // --- MAP LOGIC ---
  private initMap(): void {
    this.map = L.map('map').setView([44.4268, 26.1025], 13); // Centrat pe București
    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
      attribution: '© OpenStreetMap & CartoDB',
      maxZoom: 19
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
      // Determinăm culoarea în funcție de amenințare
      const isThreat = d.threat_level === 'THREAT';
      const color = isThreat ? '#ff003c' : '#00f3ff'; // Roșu vs Cyan
      const pulseClass = isThreat ? 'pulse-red' : ''; // Opțional: clasă CSS pentru pulsare

      if (this.markers[key]) {
        // CAZUL 1: Drona există deja pe hartă
        
        // A. Actualizăm Poziția
        this.markers[key].setLatLng([d.lat, d.lng]);
        
        // B. Actualizăm Culoarea (Aici era problema!)
        // Leaflet CircleMarker folosește setStyle pentru culori
        this.markers[key].setStyle({ color: color, fillColor: color });

        // C. Actualizăm Popup-ul (Textul)
        const popupContent = `
          <div style="text-align: center">
            <b style="color: ${color}; font-size: 1.1em">${key}</b><br>
            Status: <b>${d.threat_level || 'ANALYZING...'}</b><br>
            <hr style="border-color: #444; margin: 5px 0;">
            <small style="color: #ccc">${d.report || 'No report'}</small>
          </div>
        `;
        this.markers[key].setPopupContent(popupContent);

      } else {
        // CAZUL 2: Drona apare prima dată
        const marker = L.circleMarker([d.lat, d.lng], {
          color: color,
          fillColor: color,
          fillOpacity: 0.5,
          radius: 10,
          weight: 2
        }).addTo(this.map);

        const popupContent = `
          <div style="text-align: center">
            <b style="color: ${color}; font-size: 1.1em">${key}</b><br>
            Status: <b>${d.threat_level || 'Active'}</b>
          </div>
        `;
        
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
    console.log(`🚀 Starting simulation for ${drone.callSign}`);
    let angle = 0;
    const centerLat = 44.4268;
    const centerLng = 26.1025;
    const radius = 0.02; // Raza cercului de patrulare

    // Pornim timer-ul (la fiecare 3 secunde)
    this.simulationIntervals[drone.id] = setInterval(() => {
      angle += 0.2; // Avansăm unghiul
      
      // Calculăm noua poziție (cerc)
      const lat = centerLat + (Math.sin(angle) * radius);
      const lng = centerLng + (Math.cos(angle) * radius);

      // Scenariu Random: 1 din 5 șanse să vadă ceva periculos
      const isThreat = Math.random() > 0.8;
      const reportText = isThreat 
        ? "Visual contact: Armed convoy moving towards civilian sector." 
        : "Sector clear. Patrolling assigned perimeter.";

      const payload = {
        lat: lat,
        lng: lng,
        alt: 150 + Math.random() * 50,
        battery: Math.floor(Math.random() * 100),
        report: reportText
      };

      // Trimitem la Backend
      this.apiService.sendTelemetry(drone.callSign, payload).subscribe({
        next: () => console.log(`📡 Ping sent for ${drone.callSign}`),
        // Adăugăm un handling mic pentru erori în consolă ca să nu sperie
        error: (err) => console.warn('Ping skipped (Rate Limit/Network):', err.status)
      });

    }, 12000);
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