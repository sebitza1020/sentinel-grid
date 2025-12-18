import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import * as L from 'leaflet';
import { TelemetryService } from './services/telemetry';

@Component({
  selector: 'app-root',
  imports: [CommonModule],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit {
  private map!: L.Map;
  private markers: { [key: string]: L.CircleMarker } = {}; // Ținem evidența markerelor pe hartă
  public drones: any = {};

  constructor(private telemetryService: TelemetryService) {}

  ngOnInit() {
    this.initMap();
    
    // Ne abonăm la datele din Firebase
    this.telemetryService.dronePositions$.subscribe(data => {
      this.drones = data;
      this.updateMarkers(data);
    });
  }

  // Helper pentru HTML
  objectKeys(obj: any) {
    return Object.keys(obj);
  }

  private initMap(): void {
    // Centrăm harta pe București (sau locația ta simulată)
    this.map = L.map('map').setView([44.4268, 26.1025], 13);

    // Adăugăm un strat de hartă "Dark Matter" (arată foarte tech/defense)
    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
      attribution: '&copy; OpenStreetMap &copy; CARTO',
      maxZoom: 19
    }).addTo(this.map);
  }

  private updateMarkers(data: any) {
    for (const callSign in data) {
      const drone = data[callSign];
      
      let color = '#3388ff';
      
      if (drone.threat_level === 'SUSPICIOUS') color = 'orange';
      if (drone.threat_level === 'THREAT') color = 'red';

      if (this.markers[callSign]) {
        this.markers[callSign].setLatLng([drone.lat, drone.lng]);
        
        this.markers[callSign].setStyle({ color: color, fillColor: color });

        this.markers[callSign].setPopupContent(`
          <b>${callSign}</b><br>
          Status: <b>${drone.threat_level || 'Scanning...'}</b><br>
          Report: <i>${drone.last_report || 'No visual'}</i>
        `);
      } else {
        const marker = L.circleMarker([drone.lat, drone.lng], {
          color: 'red',
          fillColor: '#f03',
          fillOpacity: 0.8,
          radius: 10
        }).addTo(this.map);

        marker.bindPopup(`<b>${callSign}</b> initialized.`);
        this.markers[callSign] = marker;
      }
    }
  }
}
