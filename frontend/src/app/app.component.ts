import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DroneApiService } from './services/drone-api.service';
import { TelemetryService } from './services/telemetry.service';
import * as L from 'leaflet';
import { AuthService } from './services/auth.service';
import { AnalyticsPanelComponent } from './components/analytics-panel/analytics-panel.component';
import {
  DRONE_CLASSES,
  DroneCreateRequest,
  DroneTelemetry,
  emptyDroneRequest,
  FleetDrone,
  TelemetrySnapshot,
  VISION_MODE_OPTIONS,
} from './models/drone.model';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule, AnalyticsPanelComponent],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss'],
})
export class AppComponent implements OnInit {
  private map: any;
  private markers: { [key: string]: L.CircleMarker } = {};

  // UI State
  showAdmin = false;
  isLoading = false;

  // Data State
  dbDrones: FleetDrone[] = [];
  newDrone: DroneCreateRequest = emptyDroneRequest();
  readonly droneClasses = DRONE_CLASSES;
  readonly visionModeOptions = VISION_MODE_OPTIONS;
  expandedDroneId: string | null = null;

  // Ultima telemetrie live (Firebase), keyed by callSign — folosită pentru Black Box analytics
  private liveTelemetry: TelemetrySnapshot = {};

  // Atmospheric Sensors (Open-Meteo -> backend /api/weather)
  weatherData: any = null;

  // Dicționar pentru a ține minte care dronă zboară (ID -> Interval Timer)
  simulationIntervals: { [key: string]: any } = {};

  loginData = { username: '', password: '' };

  selectedDroneId: string | null = null;
  // Planul de zbor = coadă ordonată de waypoint-uri (calculată de backend cu ocolirea No-Fly Zone).
  flightPlans: { [key: string]: { lat: number; lng: number }[] } = {};
  flightPaths: { [key: string]: L.Polyline } = {};
  // Care drone au o rută de reinforcement (Fleet Commander) — le desenăm traiectoria roșu animat.
  private reinforcing: { [key: string]: boolean } = {};
  // Care drone sunt în Autonomous RTB — traiectorie ambră intermitentă spre baza cea mai apropiată.
  private rtb: { [key: string]: boolean } = {};
  // Ultima poziție simulată per dronă (id), folosită ca punct de plecare pentru rutare.
  private lastPositions: { [key: string]: { lat: number; lng: number } } = {};
  // Layer editabil cu poligoanele No-Fly Zone desenate de operator.
  private drawnItems: any;

  constructor(
    private telemetryService: TelemetryService,
    private apiService: DroneApiService,
    public authService: AuthService,
    private cdr: ChangeDetectorRef,
  ) {}

  doLogin() {
    this.authService.login(this.loginData.username, this.loginData.password).subscribe({
      next: () => {
        alert('ACCESS GRANTED. Welcome back, Commander.');
        this.loadDrones(); // Încărcăm datele după login
      },
      error: () => alert('ACCESS DENIED. Invalid credentials.'),
    });
  }

  doLogout() {
    this.authService.logout();
    this.dbDrones = []; // Golim lista vizual
  }

  ngOnInit() {
    this.initMap();
    this.setupTelemetry();
    this.setupPaths(); // ascultăm rutele de evaziune calculate de backend (WS)
    this.loadDrones(); // Încercăm încărcarea la start
    this.loadWeather(); // Citim senzorii atmosferici
    // Reîncercăm periodic: dacă primul fetch eșuează (cold start / 429), HUD-ul se
    // reface singur. Backend-ul cache-uiește ~10 min, deci apelurile sunt ieftine.
    setInterval(() => this.loadWeather(), 120000); // la fiecare 2 minute
  }

  // --- ATMOSPHERIC SENSORS LOGIC ---
  private loadWeather() {
    this.apiService.getWeather().subscribe({
      next: (data) => {
        // Păstrăm ultima valoare bună dacă backend-ul întoarce gol (204/null pe cold start)
        if (data?.current) {
          this.weatherData = data;
          this.cdr.markForCheck(); // zoneless: forțăm redesenarea HUD-ului atmosferic
          console.log('🌡️ Atmospheric sensors online:', data);
        } else {
          console.warn('⚠️ Atmospheric sensors returned no data (keeping last known).');
        }
      },
      error: (err) => {
        console.warn('⚠️ Atmospheric sensors offline:', err.status);
      },
    });
  }

  // --- MAP LOGIC ---
  private initMap(): void {
    this.map = L.map('map').setView([44.4268, 26.1025], 13);
    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
      attribution: '© OpenStreetMap & CartoDB',
      maxZoom: 19,
    }).addTo(this.map);

    // --- ASCULTĂ CLICK-URILE PE HARTĂ ---
    this.map.on('click', (e: any) => {
      if (this.selectedDroneId) {
        this.setWayPoint(this.selectedDroneId, e.latlng.lat, e.latlng.lng);
      }
    });

    void this.initGeofencing();
  }

  // --- GEOFENCING (No-Fly Zones) ---
  private async initGeofencing(): Promise<void> {
    // leaflet-draw se atașează la instanța globală L; o expunem ÎNAINTE de a-l încărca,
    // apoi îl importăm dinamic (un import static s-ar evalua înainte ca L să fie global).
    (window as any).L = L;
    await import('leaflet-draw');

    const Ldraw = L as any;
    this.drawnItems = new L.FeatureGroup();
    this.map.addLayer(this.drawnItems);

    const drawControl = new Ldraw.Control.Draw({
      position: 'bottomleft', // top corners sunt ocupate de HUD-uri

      draw: {
        // Doar poligoane (No-Fly Zones), stil cyberpunk: contur cyan + umplere roșie translucidă.
        polygon: {
          shapeOptions: { color: '#00f3ff', fillColor: '#ff003c', fillOpacity: 0.2, weight: 2 },
        },
        polyline: false,
        rectangle: false,
        circle: false,
        marker: false,
        circlemarker: false,
      },
      edit: { featureGroup: this.drawnItems },
    });
    this.map.addControl(drawControl);

    // Sincronizăm zonele la orice creare/editare/ștergere.
    this.map.on(Ldraw.Draw.Event.CREATED, (e: any) => {
      this.drawnItems.addLayer(e.layer);
      this.syncGeofences();
    });
    this.map.on(Ldraw.Draw.Event.EDITED, () => this.syncGeofences());
    this.map.on(Ldraw.Draw.Event.DELETED, () => this.syncGeofences());
  }

  /** Colectează toate poligoanele desenate și le trimite la backend (/api/geofences). */
  private syncGeofences(): void {
    const zones = this.drawnItems.getLayers().map((layer: any, index: number) => {
      const latlngs = layer.getLatLngs()[0] as L.LatLng[];
      return { id: `nfz-${index}`, polygon: latlngs.map((p) => [p.lat, p.lng]) };
    });
    this.apiService.syncGeofences(zones).subscribe({
      next: () => console.log(`🛑 Synced ${zones.length} No-Fly Zone(s) to command.`),
      error: (err) => console.warn('⚠️ Geofence sync failed:', err.status),
    });
  }

  setWayPoint(droneId: string, lat: number, lng: number) {
    const drone = this.dbDrones.find((d) => d.id === droneId);
    const callSign = drone?.callSign;
    if (!callSign) {
      console.warn('⚠️ Cannot route: unknown drone', droneId);
      return;
    }

    const start = this.currentPosition(droneId);
    console.log(`📍 Routing ${callSign} → [${lat}, ${lng}] via command pathfinder`);

    // Backend-ul calculează ruta de evaziune (ocolind No-Fly Zones) și o difuzează pe WS.
    // Folosim și răspunsul HTTP direct, pentru feedback imediat la clientul care a cerut ruta.
    this.apiService.requestRoute(callSign, [start.lat, start.lng], [lat, lng]).subscribe({
      next: (waypoints: number[][]) => this.applyRoute(droneId, waypoints),
      error: (err) => console.warn('⚠️ Route request failed:', err.status),
    });
  }

  /** Ascultă rutele difuzate de backend pe WebSocket (type: "path") și le aplică dronei. */
  private setupPaths(): void {
    this.telemetryService.dronePaths$.subscribe((msg: any) => {
      if (!msg?.callSign || !Array.isArray(msg.waypoints)) return;
      const drone = this.dbDrones.find(
        (d) => this.normalizeCallSign(d.callSign) === this.normalizeCallSign(msg.callSign),
      );
      if (!drone) return;

      const reinforcement = msg.reinforcement === true;
      const rtb = msg.rtb === true;
      this.applyRoute(drone.id, msg.waypoints, reinforcement, rtb);

      // Fleet Commander: dacă e un ordin de reinforcement, "scramble" drona (dacă nu zboară deja)
      // ca să pornească autonom spre amenințare.
      if (reinforcement && !this.simulationIntervals[drone.id]) {
        console.log(`🛡️ [COMMANDER] Scrambling ${drone.callSign} for reinforcement.`);
        this.startSimulation(drone);
      }
    });
  }

  /** Aplică o coadă de waypoint-uri (fiecare [lat,lng]) și redesenează traiectoria. */
  private applyRoute(
    droneId: string,
    waypoints: number[][],
    reinforcement = false,
    rtb = false,
  ): void {
    this.flightPlans[droneId] = waypoints.map((w) => ({ lat: w[0], lng: w[1] }));
    this.reinforcing[droneId] = reinforcement;
    this.rtb[droneId] = rtb;
    this.drawFlightPath(droneId);
    this.cdr.markForCheck();
  }

  /** Redesenează polilinia cyan punctată de la poziția curentă prin waypoint-urile rămase. */
  private drawFlightPath(droneId: string): void {
    if (this.flightPaths[droneId]) {
      this.flightPaths[droneId].remove();
      delete this.flightPaths[droneId];
    }
    const plan = this.flightPlans[droneId];
    if (!plan || plan.length === 0) return;

    const start = this.currentPosition(droneId);
    const points: L.LatLngExpression[] = [
      [start.lat, start.lng],
      ...plan.map((w) => [w.lat, w.lng] as L.LatLngExpression),
    ];
    // Precedență: RTB (ambru intermitent) > reinforcement (roșu animat) > normal (cyan).
    const rtb = this.rtb[droneId];
    const reinforce = this.reinforcing[droneId];
    let color = '#00f3ff';
    let className = '';
    if (rtb) {
      color = '#ffb300';
      className = 'rtb-path';
    } else if (reinforce) {
      color = '#ff003c';
      className = 'reinforce-path';
    }
    this.flightPaths[droneId] = L.polyline(points, {
      color,
      weight: rtb || reinforce ? 3 : 2,
      dashArray: '5, 10',
      opacity: rtb || reinforce ? 0.9 : 0.7,
      className,
    }).addTo(this.map);
  }

  private currentPosition(droneId: string): { lat: number; lng: number } {
    if (this.lastPositions[droneId]) return this.lastPositions[droneId];
    const drone = this.dbDrones.find((d) => d.id === droneId);
    const marker = drone ? this.markers[this.normalizeCallSign(drone.callSign)] : undefined;
    if (marker) {
      const p = marker.getLatLng();
      return { lat: p.lat, lng: p.lng };
    }
    const c = this.map.getCenter();
    return { lat: c.lat, lng: c.lng };
  }

  private setupTelemetry() {
    this.telemetryService.dronePositions$.subscribe((data) => {
      this.liveTelemetry = data || {};
      this.updateMarkers(data);
      this.mergeTelemetryIntoFleet(); // alimentăm panoul Black Box cu date live
      // App zoneless: callback-urile async (Firebase/HTTP) nu declanșează singure
      // change detection, așa că notificăm manual ca panoul să se redeseneze.
      this.cdr.markForCheck();
    });
  }

  /**
   * Îmbogățește lista de drone (SQL) cu telemetria live (Firebase), potrivită
   * după callSign. Reasignăm dbDrones cu o referință nouă ca ngOnChanges din
   * AnalyticsPanelComponent să se declanșeze. Câmpurile de identitate
   * (id/callSign/model) rămân cele din baza de date.
   */
  private mergeTelemetryIntoFleet() {
    if (!this.dbDrones || this.dbDrones.length === 0) return;

    const byCallSign: { [k: string]: any } = {};
    Object.keys(this.liveTelemetry || {}).forEach((k) => {
      byCallSign[k.toUpperCase()] = this.liveTelemetry[k];
    });

    this.dbDrones = this.dbDrones.map((d) => {
      const live = byCallSign[(d.callSign || '').toUpperCase()];
      if (!live) return d;
      return {
        ...d,
        lat: live.lat,
        lng: live.lng,
        alt: live.alt,
        battery: live.battery,
        report: live.report,
        threat_level: live.threat_level,
        status: live.status,
      };
    });
  }

  private updateMarkers(drones: TelemetrySnapshot) {
    Object.entries(drones).forEach(([incomingKey, telemetry]) => {
      if (typeof telemetry.lat !== 'number' || typeof telemetry.lng !== 'number') return;

      const key = this.normalizeCallSign(incomingKey);
      const staticDrone = this.dbDrones.find(
        (drone) => this.normalizeCallSign(drone.callSign) === key,
      );
      const d: DroneTelemetry & Partial<FleetDrone> = staticDrone
        ? { ...staticDrone, ...telemetry }
        : telemetry;
      const isThreat = d.threat_level === 'THREAT';
      const isRtb = d.status === 'RTB';
      // RTB (ambru) are precedență — protocol autonom de aterizare de urgență.
      const color = isRtb ? '#ffb300' : isThreat ? '#ff003c' : '#00f3ff';

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
      const popupContent = this.buildDronePopup(key, d, color, imageHtml);
      const tooltipContent = `${this.escapeHtml(key)} · ${this.escapeHtml(staticDrone?.model || 'UNREGISTERED')} · ${this.formatModelClass(staticDrone?.modelClass)}`;

      if (this.markers[key]) {
        // CAZUL 1: Drona există - Update
        this.markers[key].setLatLng([telemetry.lat, telemetry.lng]);
        this.markers[key].setStyle({ color: color, fillColor: color });
        this.markers[key].setTooltipContent(tooltipContent);
        // Doar actualizăm conținutul popup-ului dacă e deschis
        if (this.markers[key].isPopupOpen()) {
          this.markers[key].getPopup()?.setContent(popupContent);
        } else {
          // Altfel îl setăm pentru când va fi deschis
          this.markers[key].setPopupContent(popupContent);
        }
      } else {
        // CAZUL 2: Drona nouă - Creare
        const marker = L.circleMarker([telemetry.lat, telemetry.lng], {
          color: color,
          fillColor: color,
          fillOpacity: 0.7,
          radius: 12,
          weight: 3,
        }).addTo(this.map);

        marker.bindTooltip(tooltipContent, { direction: 'top', offset: [0, -10] });
        marker.bindPopup(popupContent, { maxWidth: 360 });
        this.markers[key] = marker;
      }
    });
  }

  private buildDronePopup(
    callSign: string,
    drone: DroneTelemetry & Partial<FleetDrone>,
    color: string,
    imageHtml: string,
  ): string {
    const modes = drone.visionModes?.length
      ? drone.visionModes
          .map((mode) => `<span class="dossier-chip">${this.escapeHtml(mode)}</span>`)
          .join('')
      : '<span class="dossier-chip dossier-chip-muted">Unspecified</span>';
    const metric = (value: number | undefined, unit: string) =>
      value == null ? '--' : `${this.formatNumber(value)} ${unit}`;

    return `
      <section class="drone-popup" style="--unit-accent: ${color}">
        <header class="drone-popup-header">
          <strong>${this.escapeHtml(callSign)}</strong>
          <span>${this.escapeHtml(drone.model || 'UNREGISTERED')}</span>
        </header>
        <div class="drone-popup-status">STATUS // <b>${this.escapeHtml(drone.threat_level || drone.status || 'ANALYZING')}</b></div>
        <div class="dossier-grid dossier-grid-popup">
          <div><span>CLASS</span><b>${this.formatModelClass(drone.modelClass)}</b></div>
          <div><span>TOP SPEED</span><b>${metric(drone.topSpeed, 'km/h')}</b></div>
          <div><span>RADAR</span><b>${metric(drone.radarRange, 'm')}</b></div>
          <div><span>PAYLOAD</span><b>${metric(drone.payloadCapacity, 'kg')}</b></div>
          <div><span>BATTERY</span><b>${metric(drone.batteryCapacity, 'mAh')}</b></div>
        </div>
        <div class="dossier-modes"><span>VISION MODES</span><div>${modes}</div></div>
        ${imageHtml}
        <p class="drone-popup-report">“${this.escapeHtml(drone.report || 'No report')}”</p>
      </section>`;
  }

  formatModelClass(modelClass: FleetDrone['modelClass'] | undefined): string {
    return modelClass ? modelClass.replace('_', ' ') : 'UNSPECIFIED';
  }

  formatNumber(value: number): string {
    return new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 }).format(value);
  }

  escapeHtml(value: unknown): string {
    return String(value)
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  private normalizeCallSign(callSign: string): string {
    return (callSign || '').trim().toUpperCase();
  }

  // --- FLEET MANAGEMENT LOGIC ---
  toggleAdmin() {
    this.showAdmin = !this.showAdmin;
    if (this.showAdmin) {
      this.loadDrones();
    }
    setTimeout(() => this.map?.invalidateSize(), 320);
  }

  toggleDossier(drone: FleetDrone): void {
    this.expandedDroneId = this.expandedDroneId === drone.id ? null : drone.id;
  }

  isDossierExpanded(id: string): boolean {
    return this.expandedDroneId === id;
  }

  trackDrone(_index: number, drone: FleetDrone): string {
    return drone.id;
  }

  toggleVisionMode(mode: string): void {
    const modes = this.newDrone.visionModes;
    this.newDrone.visionModes = modes.includes(mode)
      ? modes.filter((current) => current !== mode)
      : [...modes, mode];
  }

  isDeployFormValid(): boolean {
    return (
      this.newDrone.callSign.trim().length > 0 &&
      this.newDrone.model.trim().length > 0 &&
      this.newDrone.batteryCapacity > 0 &&
      this.newDrone.topSpeed > 0 &&
      this.newDrone.radarRange > 0 &&
      this.newDrone.payloadCapacity > 0 &&
      this.newDrone.visionModes.length > 0
    );
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

    // Dacă drona e deja pe hartă, plecăm de acolo (markerii sunt cheie după callSign)
    const markerKey = this.normalizeCallSign(drone.callSign);
    if (this.markers[markerKey]) {
      const pos = this.markers[markerKey].getLatLng();
      currentLat = pos.lat;
      currentLng = pos.lng;
    }
    this.lastPositions[drone.id] = { lat: currentLat, lng: currentLng };

    // Intervalul de simulare (15 secunde pentru analiza AI)
    this.simulationIntervals[drone.id] = setInterval(() => {
      // Urmăm coada de waypoint-uri (ruta de evaziune calculată de backend).
      const plan = this.flightPlans[drone.id];

      if (plan && plan.length > 0) {
        const target = plan[0];
        const distLat = target.lat - currentLat;
        const distLng = target.lng - currentLng;
        const step = 0.002; // pas fix per tick, ca mișcarea să fie vizibilă

        if (Math.abs(distLat) < 0.0005 && Math.abs(distLng) < 0.0005) {
          // Waypoint atins → trecem la următorul.
          plan.shift();
          if (plan.length === 0) {
            this.reinforcing[drone.id] = false;
            if (this.flightPaths[drone.id]) {
              this.flightPaths[drone.id].remove();
              delete this.flightPaths[drone.id];
            }
            console.log('Route complete. Holding position.');
          }
        } else {
          const angle = Math.atan2(distLat, distLng);
          currentLat += Math.sin(angle) * step;
          currentLng += Math.cos(angle) * step;
        }

        this.lastPositions[drone.id] = { lat: currentLat, lng: currentLng };
        this.drawFlightPath(drone.id); // redesenăm traiectoria rămasă
      } else {
        // Fallback fără waypoint: drift ușor.
        currentLat += (Math.random() - 0.5) * 0.001;
        currentLng += (Math.random() - 0.5) * 0.001;
        this.lastPositions[drone.id] = { lat: currentLat, lng: currentLng };
      }

      // --- SCENARIU THREAT & AI --- (Rămâne neschimbat)
      const isThreat = Math.random() > 0.8;
      const reportText = isThreat
        ? 'Visual contact: Armed convoy moving towards civilian sector.'
        : 'Sector clear. En route to waypoint.';

      const payload = {
        lat: currentLat,
        lng: currentLng,
        alt: 150 + Math.random() * 50,
        battery: Math.floor(Math.random() * 100),
        report: reportText,
      };

      this.apiService.sendTelemetry(drone.callSign, payload).subscribe({
        next: () => console.log(`📡 Telemetry sent`),
        error: (err) => console.warn('Telemetry skipped:', err.status),
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
        this.mergeTelemetryIntoFleet(); // aplicăm telemetria live curentă peste flota încărcată
        this.updateMarkers(this.liveTelemetry); // refresh popup dossiers after SQL metadata arrives
        this.isLoading = false;
        this.cdr.markForCheck(); // zoneless: redesenăm lista + panoul Black Box
        console.log('✅ Drones loaded from SQL:', data);
      },
      error: (err) => {
        console.error('❌ Failed to load drones:', err);
        this.isLoading = false;
        this.cdr.markForCheck();
        // Nu dăm alert la load automat, ca să nu stresăm userul dacă backend-ul doarme
      },
    });
  }

  addDrone() {
    if (!this.isDeployFormValid()) return;

    this.isLoading = true;
    const payload: DroneCreateRequest = {
      ...this.newDrone,
      callSign: this.normalizeCallSign(this.newDrone.callSign),
      model: this.newDrone.model.trim(),
      visionModes: [...this.newDrone.visionModes],
    };

    this.apiService.create(payload).subscribe({
      next: (resp) => {
        // Referință nouă ca panoul Black Box (ngOnChanges) să se actualizeze
        this.dbDrones = [...this.dbDrones, resp];

        this.newDrone = emptyDroneRequest();
        this.isLoading = false;
        this.cdr.markForCheck();

        console.log(`Unit ${resp.callSign} deployed successfully!`);
      },
      error: (err) => {
        console.error(err);
        alert(`DEPLOY FAILED: ${err.message}`);
        this.isLoading = false;
        this.cdr.markForCheck();
      },
    });
  }

  decommission(id: string) {
    if (confirm('⚠️ WARNING: Confirm decommissioning of this unit?')) {
      const backupList = [...this.dbDrones];
      this.dbDrones = this.dbDrones.filter((d) => d.id !== id);

      this.apiService.delete(id).subscribe({
        next: () => {
          console.log('Unit decommissioned confirmed by HQ.');
        },
        error: (err) => {
          alert('Decommission failed: ' + err.message);
          this.dbDrones = backupList;
          this.cdr.markForCheck();
        },
      });
    }
  }
}
