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
    // ... Logica ta existentă de markeri rămâne neschimbată ...
    // Dacă ai nevoie de ea, spune-mi, dar presupun că e deja acolo din codul vechi
    Object.keys(drones).forEach(key => {
        const d = drones[key];
        if (this.markers[key]) {
            this.markers[key].setLatLng([d.lat, d.lng]);
            // Update popup content if needed
        } else {
            const marker = L.circleMarker([d.lat, d.lng], {
                color: d.threat_level === 'THREAT' ? '#ff003c' : '#00f3ff',
                radius: 8
            }).addTo(this.map);
            marker.bindPopup(`<b>${key}</b><br>Status: ${d.threat_level || 'Active'}`);
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
    // Completăm obiectul cu default-uri pentru Backend
    const payload = {
      callSign: this.newDrone.callSign,
      model: this.newDrone.model,
      batteryCapacity: 10000, // Default militar
      status: 'OFFLINE'
    };

    this.apiService.create(payload).subscribe({
      next: (resp) => {
        alert(`Unit ${resp.callSign} deployed successfully!`);
        this.newDrone = { callSign: '', model: '' }; // Reset form
        this.loadDrones(); // Refresh list
        this.isLoading = false;
      },
      error: (err) => {
        console.error(err);
        alert(`DEPLOY FAILED: ${err.message || 'CORS/Backend Error'}`);
        this.isLoading = false;
      }
    });
  }

  decommission(id: string) {
    if (confirm('⚠️ WARNING: Confirm decommissioning of this unit?')) {
      this.apiService.delete(id).subscribe({
        next: () => {
          this.loadDrones();
        },
        error: (err) => alert('Decommission failed: ' + err.message)
      });
    }
  }
}