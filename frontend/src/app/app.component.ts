import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { AnalyticsPanelComponent } from './components/analytics-panel/analytics-panel.component';
import { LogoComponent } from './components/logo/logo.component';
import { TacticalMapComponent } from './components/tactical-map/tactical-map.component';
import {
  DRONE_CLASSES,
  DroneCreateRequest,
  emptyDroneRequest,
  FleetDrone,
  PathMessage,
  Position3D,
  TacticalRoute,
  TelemetrySnapshot,
  VISION_MODE_OPTIONS,
} from './models/drone.model';
import { AuthService } from './services/auth.service';
import { DroneApiService } from './services/drone-api.service';
import { TelemetryService } from './services/telemetry.service';

const DEFAULT_POSITION: Position3D = [44.4268, 26.1025, 0];

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    AnalyticsPanelComponent,
    LogoComponent,
    TacticalMapComponent,
  ],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss'],
})
export class AppComponent implements OnInit, OnDestroy {
  showAdmin = false;
  isLoading = false;

  dbDrones: FleetDrone[] = [];
  newDrone: DroneCreateRequest = emptyDroneRequest();
  readonly droneClasses = DRONE_CLASSES;
  readonly visionModeOptions = VISION_MODE_OPTIONS;
  expandedDroneId: string | null = null;

  private liveTelemetry: TelemetrySnapshot = {};
  weatherData: any = null;
  simulationIntervals: Record<string, ReturnType<typeof setInterval>> = {};
  loginData = { username: '', password: '' };

  selectedDroneId: string | null = null;
  flightPlans: Record<string, Position3D[]> = {};
  mapRoutes: Record<string, TacticalRoute> = {};
  private reinforcing: Record<string, boolean> = {};
  private rtb: Record<string, boolean> = {};
  private lastPositions: Record<string, { lat: number; lng: number; altitude: number }> = {};
  private readonly subscriptions = new Subscription();
  private weatherTimer?: ReturnType<typeof setInterval>;

  constructor(
    private readonly telemetryService: TelemetryService,
    private readonly apiService: DroneApiService,
    public readonly authService: AuthService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.setupTelemetry();
    this.setupPaths();
    this.loadDrones();
    this.loadWeather();
    this.weatherTimer = setInterval(() => this.loadWeather(), 120_000);
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    if (this.weatherTimer) clearInterval(this.weatherTimer);
    for (const timer of Object.values(this.simulationIntervals)) {
      clearInterval(timer);
    }
  }

  doLogin(): void {
    this.authService.login(this.loginData.username, this.loginData.password).subscribe({
      next: () => {
        alert('ACCESS GRANTED. Welcome back, Commander.');
        this.loadDrones();
      },
      error: () => alert('ACCESS DENIED. Invalid credentials.'),
    });
  }

  doLogout(): void {
    this.authService.logout();
    this.dbDrones = [];
  }

  onWaypointSelected(position: Position3D): void {
    if (this.selectedDroneId) {
      this.setWayPoint(this.selectedDroneId, position);
    }
  }

  setWayPoint(droneId: string, destination: Position3D): void {
    const drone = this.dbDrones.find((candidate) => candidate.id === droneId);
    if (!drone?.callSign) {
      console.warn('Cannot route an unknown drone:', droneId);
      return;
    }

    const start = this.currentPosition(droneId);
    this.apiService.requestRoute(drone.callSign, start, destination).subscribe({
      next: (waypoints) => this.applyRoute(droneId, waypoints),
      error: (error) => {
        if (error.status === 409) {
          console.warn('Route blocked by restricted airspace.');
        } else {
          console.warn('Route request failed:', error.status);
        }
      },
    });
  }

  private setupPaths(): void {
    this.subscriptions.add(
      this.telemetryService.dronePaths$.subscribe((message: PathMessage) => {
        if (!message.callSign || !Array.isArray(message.waypoints)) return;
        const drone = this.dbDrones.find(
          (candidate) =>
            this.normalizeCallSign(candidate.callSign) === this.normalizeCallSign(message.callSign),
        );
        if (!drone) return;

        this.applyRoute(
          drone.id,
          message.waypoints,
          message.reinforcement === true,
          message.rtb === true,
        );
        if (message.reinforcement && !this.simulationIntervals[drone.id]) {
          this.startSimulation(drone);
        }
      }),
    );
  }

  private applyRoute(
    droneId: string,
    waypoints: number[][],
    reinforcement = false,
    rtb = false,
  ): void {
    this.flightPlans[droneId] = waypoints.map((waypoint) => [
      waypoint[0],
      waypoint[1],
      Number.isFinite(waypoint[2]) ? waypoint[2] : 0,
    ]);
    this.reinforcing[droneId] = reinforcement;
    this.rtb[droneId] = rtb;
    this.refreshRoute(droneId);
    this.cdr.markForCheck();
  }

  private refreshRoute(droneId: string): void {
    const drone = this.dbDrones.find((candidate) => candidate.id === droneId);
    const plan = this.flightPlans[droneId];
    if (!drone || !plan?.length) {
      const next = { ...this.mapRoutes };
      delete next[droneId];
      this.mapRoutes = next;
      return;
    }
    this.mapRoutes = {
      ...this.mapRoutes,
      [droneId]: {
        callSign: drone.callSign,
        waypoints: [...plan],
        reinforcement: this.reinforcing[droneId] === true,
        rtb: this.rtb[droneId] === true,
      },
    };
  }

  private currentPosition(droneId: string): Position3D {
    const last = this.lastPositions[droneId];
    if (last) return [last.lat, last.lng, last.altitude];

    const drone = this.dbDrones.find((candidate) => candidate.id === droneId);
    if (drone && typeof drone.lat === 'number' && typeof drone.lng === 'number') {
      return [drone.lat, drone.lng, this.altitudeOf(drone)];
    }

    if (drone) {
      const live = this.telemetryFor(drone.callSign);
      if (typeof live?.lat === 'number' && typeof live.lng === 'number') {
        return [live.lat, live.lng, this.altitudeOf(live)];
      }
    }
    return [...DEFAULT_POSITION];
  }

  private setupTelemetry(): void {
    this.subscriptions.add(
      this.telemetryService.dronePositions$.subscribe((data) => {
        this.liveTelemetry = data || {};
        this.mergeTelemetryIntoFleet();
        this.cdr.markForCheck();
      }),
    );
  }

  private mergeTelemetryIntoFleet(): void {
    if (!this.dbDrones.length) return;
    const byCallSign: Record<string, any> = {};
    for (const key of Object.keys(this.liveTelemetry)) {
      byCallSign[this.normalizeCallSign(key)] = this.liveTelemetry[key];
    }

    this.dbDrones = this.dbDrones.map((drone) => {
      const live = byCallSign[this.normalizeCallSign(drone.callSign)];
      if (!live) return drone;
      return {
        ...drone,
        lat: live.lat,
        lng: live.lng,
        alt: live.alt ?? live.altitude,
        battery: live.battery,
        report: live.report,
        threat_level: live.threat_level,
        status: live.status,
      };
    });
  }

  private loadWeather(): void {
    this.apiService.getWeather().subscribe({
      next: (data) => {
        if (data?.current) {
          this.weatherData = data;
          this.cdr.markForCheck();
        }
      },
      error: (error) => console.warn('Atmospheric sensors offline:', error.status),
    });
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

  toggleAdmin(): void {
    this.showAdmin = !this.showAdmin;
    if (this.showAdmin) this.loadDrones();
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

  toggleSimulation(drone: FleetDrone): void {
    if (this.simulationIntervals[drone.id]) {
      this.stopSimulation(drone);
    } else {
      this.startSimulation(drone);
    }
  }

  startSimulation(drone: FleetDrone): void {
    this.selectedDroneId = drone.id;
    const initial = this.currentPosition(drone.id);
    let currentLat = initial[0];
    let currentLng = initial[1];
    let currentAltitude = initial[2];
    this.lastPositions[drone.id] = {
      lat: currentLat,
      lng: currentLng,
      altitude: currentAltitude,
    };

    this.simulationIntervals[drone.id] = setInterval(() => {
      const plan = this.flightPlans[drone.id];
      if (plan?.length) {
        const [targetLat, targetLng, targetAltitude] = plan[0];
        const deltaLat = targetLat - currentLat;
        const deltaLng = targetLng - currentLng;
        const distance = Math.hypot(deltaLat, deltaLng);
        const step = 0.002;

        if (distance < 0.0005) {
          currentLat = targetLat;
          currentLng = targetLng;
          currentAltitude = targetAltitude;
          this.flightPlans[drone.id] = plan.slice(1);
          if (this.flightPlans[drone.id].length === 0) {
            this.reinforcing[drone.id] = false;
            this.rtb[drone.id] = false;
          }
        } else {
          const ratio = Math.min(1, step / distance);
          currentLat += deltaLat * ratio;
          currentLng += deltaLng * ratio;
          currentAltitude += (targetAltitude - currentAltitude) * ratio;
        }
        this.refreshRoute(drone.id);
      } else {
        currentLat += (Math.random() - 0.5) * 0.001;
        currentLng += (Math.random() - 0.5) * 0.001;
      }

      this.lastPositions[drone.id] = {
        lat: currentLat,
        lng: currentLng,
        altitude: Math.max(0, currentAltitude),
      };
      const report =
        Math.random() > 0.8
          ? 'Visual contact: Armed convoy moving towards civilian sector.'
          : 'Sector clear. En route to waypoint.';

      this.apiService
        .sendTelemetry(drone.callSign, {
          lat: currentLat,
          lng: currentLng,
          alt: Math.max(0, currentAltitude),
          battery: Math.floor(Math.random() * 100),
          report,
        })
        .subscribe({
          error: (error) => console.warn('Telemetry skipped:', error.status),
        });
    }, 15_000);
  }

  stopSimulation(drone: FleetDrone): void {
    const timer = this.simulationIntervals[drone.id];
    if (timer) {
      clearInterval(timer);
      delete this.simulationIntervals[drone.id];
    }
  }

  isSimulating(id: string): boolean {
    return !!this.simulationIntervals[id];
  }

  loadDrones(): void {
    this.isLoading = true;
    this.apiService.getAll().subscribe({
      next: (data) => {
        this.dbDrones = data;
        this.mergeTelemetryIntoFleet();
        this.isLoading = false;
        for (const droneId of Object.keys(this.flightPlans)) {
          this.refreshRoute(droneId);
        }
        this.cdr.markForCheck();
      },
      error: (error) => {
        console.error('Failed to load drones:', error);
        this.isLoading = false;
        this.cdr.markForCheck();
      },
    });
  }

  addDrone(): void {
    if (!this.isDeployFormValid()) return;
    this.isLoading = true;
    const payload: DroneCreateRequest = {
      ...this.newDrone,
      callSign: this.normalizeCallSign(this.newDrone.callSign),
      model: this.newDrone.model.trim(),
      visionModes: [...this.newDrone.visionModes],
    };

    this.apiService.create(payload).subscribe({
      next: (response) => {
        this.dbDrones = [...this.dbDrones, response];
        this.newDrone = emptyDroneRequest();
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (error) => {
        alert(`DEPLOY FAILED: ${error.message}`);
        this.isLoading = false;
        this.cdr.markForCheck();
      },
    });
  }

  decommission(id: string): void {
    if (!confirm('WARNING: Confirm decommissioning of this unit?')) return;
    const backup = [...this.dbDrones];
    this.dbDrones = this.dbDrones.filter((drone) => drone.id !== id);
    this.apiService.delete(id).subscribe({
      error: (error) => {
        alert(`Decommission failed: ${error.message}`);
        this.dbDrones = backup;
        this.cdr.markForCheck();
      },
    });
  }

  private altitudeOf(value: { alt?: number; altitude?: number }): number {
    const altitude = value.alt ?? value.altitude ?? 0;
    return Number.isFinite(altitude) && altitude >= 0 ? altitude : 0;
  }

  private telemetryFor(callSign: string): any {
    const key = Object.keys(this.liveTelemetry).find(
      (candidate) => this.normalizeCallSign(candidate) === this.normalizeCallSign(callSign),
    );
    return key ? this.liveTelemetry[key] : undefined;
  }

  private normalizeCallSign(callSign: string): string {
    return (callSign || '').trim().toUpperCase();
  }
}
