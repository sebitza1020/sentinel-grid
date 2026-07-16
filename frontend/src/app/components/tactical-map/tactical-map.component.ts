import { CommonModule } from '@angular/common';
import {
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  ViewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { FleetDrone, GeofenceVolume, Position3D, TacticalRoute } from '../../models/drone.model';
import { DroneApiService } from '../../services/drone-api.service';

const MAP_CENTER: [number, number] = [26.1025, 44.4268];
const DEFAULT_MIN_ALTITUDE = 0;
const DEFAULT_MAX_ALTITUDE = 500;

interface DroneMarkerState {
  marker: any;
  popup: any;
  element: HTMLElement;
}

interface DrawFeature {
  id?: string | number;
  geometry?: {
    type?: string;
    coordinates?: number[][][];
  };
  properties?: Record<string, unknown>;
}

export function normalizeAltitude(drone: { alt?: number; altitude?: number }): number {
  const value = drone.alt ?? drone.altitude ?? 0;
  return Number.isFinite(value) && value >= 0 ? value : 0;
}

export function toGeoJsonRing(polygon: number[][]): number[][] {
  const ring = polygon.map(([lat, lng]) => [lng, lat]);
  if (
    ring.length > 0 &&
    (ring[0][0] !== ring[ring.length - 1][0] || ring[0][1] !== ring[ring.length - 1][1])
  ) {
    ring.push([...ring[0]]);
  }
  return ring;
}

export function toBackendPolygon(ring: number[][]): number[][] {
  const openRing =
    ring.length > 1 &&
    ring[0][0] === ring[ring.length - 1][0] &&
    ring[0][1] === ring[ring.length - 1][1]
      ? ring.slice(0, -1)
      : ring;
  return openRing.map(([lng, lat]) => [lat, lng]);
}

export function isValidAltitudeBand(minAltitude: number, maxAltitude: number): boolean {
  return (
    Number.isFinite(minAltitude) &&
    Number.isFinite(maxAltitude) &&
    minAltitude >= 0 &&
    maxAltitude > minAltitude
  );
}

@Component({
  selector: 'app-tactical-map',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './tactical-map.component.html',
  styleUrls: ['./tactical-map.component.scss'],
})
export class TacticalMapComponent implements AfterViewInit, OnChanges, OnDestroy {
  @ViewChild('mapContainer', { static: true })
  mapContainer!: ElementRef<HTMLElement>;

  @Input() drones: FleetDrone[] = [];
  @Input() selectedDroneId: string | null = null;
  @Input() routes: Record<string, TacticalRoute> = {};

  @Output() waypointSelected = new EventEmitter<Position3D>();

  mapUnavailable = false;
  mapReady = false;
  selectedZoneId: string | null = null;
  selectedMinAltitude = DEFAULT_MIN_ALTITUDE;
  selectedMaxAltitude = DEFAULT_MAX_ALTITUDE;
  altitudeError = '';

  private mapboxgl: any;
  private map: any;
  private draw: any;
  private resizeObserver?: ResizeObserver;
  private animationFrame?: number;
  private geofenceSubscription?: Subscription;
  private readonly markerStates = new Map<string, DroneMarkerState>();
  private destroyed = false;

  private readonly handleMapClick = (event: any): void => {
    if (!this.selectedDroneId || !this.map) return;
    const drawFeatureHit = this.map
      .queryRenderedFeatures(event.point)
      .some((feature: any) => String(feature.source || '').startsWith('mapbox-gl-draw'));
    if (drawFeatureHit || (this.draw && this.draw.getMode() !== 'simple_select')) return;

    const drone = this.drones.find((candidate) => candidate.id === this.selectedDroneId);
    this.waypointSelected.emit([
      event.lngLat.lat,
      event.lngLat.lng,
      drone ? normalizeAltitude(drone) : 0,
    ]);
  };

  private readonly handleDrawCreate = (event: any): void => {
    for (const feature of event.features || []) {
      const id = String(feature.id || `nfz-${crypto.randomUUID()}`);
      this.draw.setFeatureProperty(feature.id, 'zoneId', id);
      this.draw.setFeatureProperty(feature.id, 'minAltitude', DEFAULT_MIN_ALTITUDE);
      this.draw.setFeatureProperty(feature.id, 'maxAltitude', DEFAULT_MAX_ALTITUDE);
      this.selectedZoneId = String(feature.id);
      this.selectedMinAltitude = DEFAULT_MIN_ALTITUDE;
      this.selectedMaxAltitude = DEFAULT_MAX_ALTITUDE;
    }
    this.commitDrawChanges();
    this.cdr.markForCheck();
  };

  private readonly handleDrawUpdate = (): void => this.commitDrawChanges();

  private readonly handleDrawDelete = (): void => {
    this.selectedZoneId = null;
    this.commitDrawChanges();
    this.cdr.markForCheck();
  };

  private readonly handleSelectionChange = (event: any): void => {
    const feature = event.features?.[0] as DrawFeature | undefined;
    this.selectZone(feature);
  };

  constructor(
    private readonly apiService: DroneApiService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngAfterViewInit(): void {
    void this.initializeMap();
  }

  ngOnChanges(): void {
    if (this.mapReady) {
      this.renderDrones();
      this.renderRoutes();
    }
  }

  applyAltitudeBand(): void {
    if (
      !this.draw ||
      !this.selectedZoneId ||
      !isValidAltitudeBand(this.selectedMinAltitude, this.selectedMaxAltitude)
    ) {
      this.altitudeError = 'MAX ALTITUDE MUST BE GREATER THAN MIN ALTITUDE.';
      return;
    }

    this.altitudeError = '';
    this.draw.setFeatureProperty(this.selectedZoneId, 'minAltitude', this.selectedMinAltitude);
    this.draw.setFeatureProperty(this.selectedZoneId, 'maxAltitude', this.selectedMaxAltitude);
    this.commitDrawChanges();
    this.cdr.markForCheck();
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.geofenceSubscription?.unsubscribe();
    this.resizeObserver?.disconnect();
    if (this.animationFrame !== undefined) {
      cancelAnimationFrame(this.animationFrame);
    }
    for (const state of this.markerStates.values()) {
      state.popup.remove();
      state.marker.remove();
    }
    this.markerStates.clear();
    if (this.map) {
      this.map.off('click', this.handleMapClick);
      this.map.off('draw.create', this.handleDrawCreate);
      this.map.off('draw.update', this.handleDrawUpdate);
      this.map.off('draw.delete', this.handleDrawDelete);
      this.map.off('draw.selectionchange', this.handleSelectionChange);
      if (this.draw) {
        this.map.removeControl(this.draw);
      }
      this.map.remove();
    }
  }

  private async initializeMap(): Promise<void> {
    const testMode = window.__SENTINEL_MAP_TEST_MODE__ === true;
    const token = window.__SENTINEL_CONFIG__?.mapboxAccessToken?.trim();
    if (!testMode && !token) {
      this.mapUnavailable = true;
      this.cdr.markForCheck();
      return;
    }

    try {
      const [mapboxModule, drawModule] = await Promise.all([
        import('mapbox-gl'),
        import('@mapbox/mapbox-gl-draw'),
      ]);
      if (this.destroyed) return;

      this.mapboxgl = mapboxModule.default;
      const MapboxDraw = drawModule.default;
      const style = testMode
        ? ({
            version: 8,
            sources: {},
            layers: [
              {
                id: 'background',
                type: 'background',
                paint: { 'background-color': '#071018' },
              },
            ],
          } as any)
        : 'mapbox://styles/mapbox/standard';

      this.map = new this.mapboxgl.Map({
        accessToken: token,
        container: this.mapContainer.nativeElement,
        style,
        center: MAP_CENTER,
        zoom: 13.5,
        pitch: 62,
        bearing: -18,
        antialias: true,
        testMode,
        config: testMode
          ? undefined
          : {
              basemap: {
                theme: 'monochrome',
                lightPreset: 'night',
                show3dObjects: false,
              },
            },
      } as any);

      this.map.addControl(
        new this.mapboxgl.NavigationControl({ visualizePitch: true }),
        'bottom-right',
      );
      this.map.once('load', () => {
        if (this.destroyed) return;
        if (!testMode) {
          this.addTerrainAndBuildings();
        }
        this.addOperationalLayers();
        this.initializeDraw(MapboxDraw);
        this.map.on('click', this.handleMapClick);
        this.mapReady = true;
        this.renderDrones();
        this.renderRoutes();
        this.loadGeofences();
        this.animateRoutes();
        this.cdr.markForCheck();
      });

      this.resizeObserver = new ResizeObserver(() => this.map?.resize());
      this.resizeObserver.observe(this.mapContainer.nativeElement.parentElement!);
    } catch (error) {
      console.error('3D tactical map initialization failed:', error);
      this.mapUnavailable = true;
      this.cdr.markForCheck();
    }
  }

  private addTerrainAndBuildings(): void {
    if (!this.map.getSource('sentinel-dem')) {
      this.map.addSource('sentinel-dem', {
        type: 'raster-dem',
        url: 'mapbox://mapbox.mapbox-terrain-dem-v1',
        tileSize: 512,
        maxzoom: 14,
      });
      this.map.setTerrain({ source: 'sentinel-dem', exaggeration: 1.25 });
    }
    if (!this.map.getSource('sentinel-streets')) {
      this.map.addSource('sentinel-streets', {
        type: 'vector',
        url: 'mapbox://mapbox.mapbox-streets-v8',
      });
    }
    if (!this.map.getLayer('sentinel-buildings')) {
      this.map.addLayer({
        id: 'sentinel-buildings',
        type: 'fill-extrusion',
        source: 'sentinel-streets',
        'source-layer': 'building',
        slot: 'middle',
        minzoom: 14,
        filter: ['==', ['get', 'extrude'], 'true'],
        paint: {
          'fill-extrusion-color': '#19323d',
          'fill-extrusion-height': ['coalesce', ['get', 'height'], 8],
          'fill-extrusion-base': ['coalesce', ['get', 'min_height'], 0],
          'fill-extrusion-opacity': 0.72,
          'fill-extrusion-emissive-strength': 0.12,
        },
      } as any);
    }
  }

  private addOperationalLayers(): void {
    this.map.addSource('sentinel-drone-footprints', {
      type: 'geojson',
      data: this.featureCollection([]),
    });
    this.map.addLayer({
      id: 'sentinel-drone-footprints',
      type: 'circle',
      source: 'sentinel-drone-footprints',
      slot: 'top',
      paint: {
        'circle-radius': 8,
        'circle-color': ['get', 'color'],
        'circle-opacity': 0.2,
        'circle-stroke-color': ['get', 'color'],
        'circle-stroke-opacity': 0.75,
        'circle-stroke-width': 1.5,
      },
    } as any);

    this.map.addSource('sentinel-routes', {
      type: 'geojson',
      data: this.featureCollection([]),
      lineMetrics: true,
    });
    const elevationExpression = [
      'at-interpolated',
      ['*', ['line-progress'], ['-', ['length', ['get', 'elevations']], 1]],
      ['get', 'elevations'],
    ];
    this.addRouteLayer('sentinel-route-normal', 'normal', '#00f3ff', 0.72, elevationExpression);
    this.addRouteLayer(
      'sentinel-route-reinforcement',
      'reinforcement',
      '#ff003c',
      0.95,
      elevationExpression,
    );
    this.addRouteLayer('sentinel-route-rtb', 'rtb', '#ffb300', 0.95, elevationExpression);

    this.map.addSource('sentinel-nfz-volumes', {
      type: 'geojson',
      data: this.featureCollection([]),
    });
    this.map.addLayer({
      id: 'sentinel-nfz-extrusion',
      type: 'fill-extrusion',
      source: 'sentinel-nfz-volumes',
      slot: 'top',
      paint: {
        'fill-extrusion-color': '#ff003c',
        'fill-extrusion-base': ['get', 'minAltitude'],
        'fill-extrusion-height': ['get', 'maxAltitude'],
        'fill-extrusion-opacity': 0.24,
        'fill-extrusion-vertical-gradient': true,
        'fill-extrusion-emissive-strength': 0.45,
      },
    } as any);
    this.map.addLayer({
      id: 'sentinel-nfz-outline',
      type: 'line',
      source: 'sentinel-nfz-volumes',
      slot: 'top',
      paint: {
        'line-color': '#00f3ff',
        'line-width': 2,
        'line-opacity': 0.9,
        'line-z-offset': ['get', 'maxAltitude'],
        'line-elevation-reference': 'ground',
      },
    } as any);

    this.map.addSource('sentinel-nfz-labels', {
      type: 'geojson',
      data: this.featureCollection([]),
    });
    this.map.addLayer({
      id: 'sentinel-nfz-labels',
      type: 'symbol',
      source: 'sentinel-nfz-labels',
      slot: 'top',
      layout: {
        'text-field': [
          'concat',
          ['get', 'zoneId'],
          ' // ',
          ['to-string', ['get', 'minAltitude']],
          '–',
          ['to-string', ['get', 'maxAltitude']],
          'm AGL',
        ],
        'text-size': 11,
        'text-font': ['DIN Pro Medium', 'Arial Unicode MS Regular'],
        'symbol-z-elevate': true,
      },
      paint: {
        'text-color': '#ff9aae',
        'text-halo-color': '#071018',
        'text-halo-width': 1.5,
        'symbol-z-offset': ['get', 'maxAltitude'],
      },
    } as any);
  }

  private addRouteLayer(
    id: string,
    kind: string,
    color: string,
    opacity: number,
    elevationExpression: unknown[],
  ): void {
    this.map.addLayer({
      id,
      type: 'line',
      source: 'sentinel-routes',
      slot: 'top',
      filter: ['==', ['get', 'kind'], kind],
      layout: { 'line-join': 'round', 'line-cap': 'round' },
      paint: {
        'line-color': color,
        'line-width': kind === 'normal' ? 2.5 : 4,
        'line-opacity': opacity,
        'line-dasharray': [2, 3],
        'line-emissive-strength': kind === 'normal' ? 0.6 : 1,
        'line-z-offset': elevationExpression,
        'line-elevation-reference': 'ground',
        'line-occlusion-opacity': 0.35,
      },
    } as any);
  }

  private initializeDraw(MapboxDraw: any): void {
    this.draw = new MapboxDraw({
      displayControlsDefault: false,
      controls: { polygon: true, trash: true },
      userProperties: true,
    });
    this.map.addControl(this.draw, 'bottom-left');
    this.map.on('draw.create', this.handleDrawCreate);
    this.map.on('draw.update', this.handleDrawUpdate);
    this.map.on('draw.delete', this.handleDrawDelete);
    this.map.on('draw.selectionchange', this.handleSelectionChange);
  }

  private loadGeofences(): void {
    this.geofenceSubscription = this.apiService.getGeofences().subscribe({
      next: (zones) => {
        for (const zone of zones) {
          this.draw.add({
            id: zone.id,
            type: 'Feature',
            properties: {
              zoneId: zone.id,
              minAltitude: zone.minAltitude ?? DEFAULT_MIN_ALTITUDE,
              maxAltitude: zone.maxAltitude ?? DEFAULT_MAX_ALTITUDE,
            },
            geometry: {
              type: 'Polygon',
              coordinates: [toGeoJsonRing(zone.polygon)],
            },
          });
        }
        this.renderGeofences();
      },
      error: (error) => console.warn('Restricted-airspace registry unavailable:', error.status),
    });
  }

  private selectZone(feature?: DrawFeature): void {
    if (!feature?.id) {
      this.selectedZoneId = null;
      this.altitudeError = '';
      this.cdr.markForCheck();
      return;
    }
    this.selectedZoneId = String(feature.id);
    this.selectedMinAltitude = this.numberProperty(
      feature.properties?.['minAltitude'],
      DEFAULT_MIN_ALTITUDE,
    );
    this.selectedMaxAltitude = this.numberProperty(
      feature.properties?.['maxAltitude'],
      DEFAULT_MAX_ALTITUDE,
    );
    this.altitudeError = '';
    this.cdr.markForCheck();
  }

  private commitDrawChanges(): void {
    const zones = this.currentGeofences();
    this.renderGeofences(zones);
    this.geofenceSubscription?.unsubscribe();
    this.geofenceSubscription = this.apiService.syncGeofences(zones).subscribe({
      error: (error) => console.warn('Restricted-airspace sync failed:', error.status),
    });
  }

  private currentGeofences(): GeofenceVolume[] {
    const features = (this.draw?.getAll().features || []) as DrawFeature[];
    return features
      .map((feature) => this.drawFeatureToVolume(feature))
      .filter((zone): zone is GeofenceVolume => zone !== null);
  }

  private drawFeatureToVolume(feature: DrawFeature): GeofenceVolume | null {
    const ring = feature.geometry?.coordinates?.[0];
    if (feature.geometry?.type !== 'Polygon' || !ring || ring.length < 4) return null;
    const minAltitude = this.numberProperty(
      feature.properties?.['minAltitude'],
      DEFAULT_MIN_ALTITUDE,
    );
    const maxAltitude = this.numberProperty(
      feature.properties?.['maxAltitude'],
      DEFAULT_MAX_ALTITUDE,
    );
    if (!isValidAltitudeBand(minAltitude, maxAltitude)) return null;

    return {
      id: String(feature.properties?.['zoneId'] || feature.id),
      polygon: toBackendPolygon(ring),
      minAltitude,
      maxAltitude,
    };
  }

  private renderGeofences(zones = this.currentGeofences()): void {
    if (!this.mapReady && !this.map?.getSource('sentinel-nfz-volumes')) return;
    const volumeFeatures = zones.map((zone) => ({
      type: 'Feature',
      id: zone.id,
      properties: {
        zoneId: zone.id,
        minAltitude: zone.minAltitude,
        maxAltitude: zone.maxAltitude,
      },
      geometry: {
        type: 'Polygon',
        coordinates: [toGeoJsonRing(zone.polygon)],
      },
    }));
    const labelFeatures = zones.map((zone) => ({
      type: 'Feature',
      id: `${zone.id}-label`,
      properties: {
        zoneId: zone.id,
        minAltitude: zone.minAltitude,
        maxAltitude: zone.maxAltitude,
      },
      geometry: {
        type: 'Point',
        coordinates: this.polygonCentroid(toGeoJsonRing(zone.polygon)),
      },
    }));
    this.setSourceData('sentinel-nfz-volumes', this.featureCollection(volumeFeatures));
    this.setSourceData('sentinel-nfz-labels', this.featureCollection(labelFeatures));
  }

  private renderDrones(): void {
    if (!this.mapReady) return;
    const activeKeys = new Set<string>();
    const footprints: any[] = [];

    for (const drone of this.drones) {
      if (typeof drone.lat !== 'number' || typeof drone.lng !== 'number') continue;
      const key = this.normalizeCallSign(drone.callSign);
      activeKeys.add(key);
      const altitude = normalizeAltitude(drone);
      const color =
        drone.status === 'RTB'
          ? '#ffb300'
          : drone.threat_level === 'THREAT'
            ? '#ff003c'
            : '#00f3ff';
      footprints.push({
        type: 'Feature',
        id: key,
        properties: { color },
        geometry: { type: 'Point', coordinates: [drone.lng, drone.lat] },
      });

      const popupHtml = this.buildDronePopup(drone, color);
      const existing = this.markerStates.get(key);
      if (existing) {
        existing.marker.setLngLat([drone.lng, drone.lat]).setAltitude(altitude);
        existing.popup.setLngLat([drone.lng, drone.lat]).setAltitude(altitude).setHTML(popupHtml);
        this.updateMarkerElement(existing.element, drone, color, altitude);
      } else {
        const element = this.createMarkerElement(drone, color, altitude);
        const popup = new this.mapboxgl.Popup({
          maxWidth: '360px',
          className: 'sentinel-drone-popup',
        })
          .setLngLat([drone.lng, drone.lat])
          .setAltitude(altitude)
          .setHTML(popupHtml);
        const marker = new this.mapboxgl.Marker({
          element,
          anchor: 'center',
          altitude,
          occludedOpacity: 0.35,
        })
          .setLngLat([drone.lng, drone.lat])
          .setPopup(popup)
          .addTo(this.map);
        this.markerStates.set(key, { marker, popup, element });
      }
    }

    for (const [key, state] of this.markerStates.entries()) {
      if (!activeKeys.has(key)) {
        state.popup.remove();
        state.marker.remove();
        this.markerStates.delete(key);
      }
    }
    this.setSourceData('sentinel-drone-footprints', this.featureCollection(footprints));
  }

  private renderRoutes(): void {
    if (!this.mapReady) return;
    const features: any[] = [];
    for (const route of Object.values(this.routes)) {
      const drone = this.drones.find(
        (candidate) =>
          this.normalizeCallSign(candidate.callSign) === this.normalizeCallSign(route.callSign),
      );
      if (
        !drone ||
        typeof drone.lat !== 'number' ||
        typeof drone.lng !== 'number' ||
        route.waypoints.length === 0
      ) {
        continue;
      }
      const points: Position3D[] = [
        [drone.lat, drone.lng, normalizeAltitude(drone)],
        ...route.waypoints,
      ];
      features.push({
        type: 'Feature',
        properties: {
          callSign: route.callSign,
          kind: route.rtb ? 'rtb' : route.reinforcement ? 'reinforcement' : 'normal',
          elevations: points.map((point) => point[2]),
        },
        geometry: {
          type: 'LineString',
          coordinates: points.map((point) => [point[1], point[0]]),
        },
      });
    }
    this.setSourceData('sentinel-routes', this.featureCollection(features));
  }

  private animateRoutes = (): void => {
    if (this.destroyed || !this.map) return;
    const time = performance.now();
    const reinforcementOpacity = 0.62 + (Math.sin(time / 260) + 1) * 0.18;
    const rtbOpacity = 0.25 + (Math.sin(time / 125) + 1) * 0.36;
    if (this.map.getLayer('sentinel-route-reinforcement')) {
      this.map.setPaintProperty(
        'sentinel-route-reinforcement',
        'line-opacity',
        reinforcementOpacity,
      );
    }
    if (this.map.getLayer('sentinel-route-rtb')) {
      this.map.setPaintProperty('sentinel-route-rtb', 'line-opacity', rtbOpacity);
    }
    this.animationFrame = requestAnimationFrame(this.animateRoutes);
  };

  private createMarkerElement(drone: FleetDrone, color: string, altitude: number): HTMLElement {
    const element = document.createElement('div');
    element.className = 'sentinel-drone-marker';
    element.tabIndex = 0;
    element.setAttribute('role', 'button');
    this.updateMarkerElement(element, drone, color, altitude);
    return element;
  }

  private updateMarkerElement(
    element: HTMLElement,
    drone: FleetDrone,
    color: string,
    altitude: number,
  ): void {
    element.dataset['callSign'] = drone.callSign;
    element.dataset['altitude'] = String(Math.round(altitude));
    element.setAttribute(
      'aria-label',
      `${drone.callSign}, altitude ${Math.round(altitude)} metres AGL`,
    );
    element.style.setProperty('--unit-color', color);
    element.innerHTML = `
      <svg viewBox="0 0 64 64" aria-hidden="true">
        <path class="unit-wing" d="M4 31 25 21 32 5 39 21 60 31 39 37 32 59 25 37Z"></path>
        <circle cx="32" cy="31" r="7"></circle>
        <path class="unit-reticle" d="M32 0v10M32 52v12M0 31h12M52 31h12"></path>
      </svg>
      <span class="unit-altitude">${Math.round(altitude)}m</span>
    `;
  }

  private buildDronePopup(drone: FleetDrone, color: string): string {
    const modes = drone.visionModes?.length
      ? drone.visionModes
          .map((mode) => `<span class="dossier-chip">${this.escapeHtml(mode)}</span>`)
          .join('')
      : '<span class="dossier-chip dossier-chip-muted">Unspecified</span>';
    const metric = (value: number | undefined, unit: string) =>
      value == null ? '--' : `${this.formatNumber(value)} ${unit}`;
    const reconImage =
      drone.threat_level === 'THREAT' && drone.report
        ? `
          <div class="drone-popup-recon">
            <img
              src="https://image.pollinations.ai/prompt/${encodeURIComponent(
                `${drone.report.replace('Visual contact:', '').trim()} drone surveillance view night vision grainy`,
              )}?width=300&height=200&nologo=true"
              alt="AI recon simulation"
            />
            <span>AI RECON SIMULATION</span>
          </div>`
        : '';
    return `
      <section class="drone-popup" style="--unit-accent: ${color}">
        <header class="drone-popup-header">
          <strong>${this.escapeHtml(drone.callSign)}</strong>
          <span>${this.escapeHtml(drone.model || 'UNREGISTERED')}</span>
        </header>
        <div class="drone-popup-status">ALTITUDE // <b>${Math.round(normalizeAltitude(drone))} m AGL</b></div>
        <div class="drone-popup-status">STATUS // <b>${this.escapeHtml(drone.threat_level || drone.status || 'ANALYZING')}</b></div>
        <div class="dossier-grid-popup">
          <div><span>CLASS</span><b>${this.formatModelClass(drone.modelClass)}</b></div>
          <div><span>TOP SPEED</span><b>${metric(drone.topSpeed, 'km/h')}</b></div>
          <div><span>RADAR</span><b>${metric(drone.radarRange, 'm')}</b></div>
          <div><span>PAYLOAD</span><b>${metric(drone.payloadCapacity, 'kg')}</b></div>
          <div><span>BATTERY</span><b>${metric(drone.batteryCapacity, 'mAh')}</b></div>
        </div>
        <div class="dossier-modes"><span>VISION MODES</span><div>${modes}</div></div>
        ${reconImage}
        <p class="drone-popup-report">“${this.escapeHtml(drone.report || 'No report')}”</p>
      </section>`;
  }

  private setSourceData(sourceId: string, data: any): void {
    this.map?.getSource(sourceId)?.setData(data);
  }

  private featureCollection(features: any[]): any {
    return { type: 'FeatureCollection', features };
  }

  private polygonCentroid(ring: number[][]): number[] {
    const open = ring.slice(0, -1);
    const total = open.reduce((sum, point) => [sum[0] + point[0], sum[1] + point[1]], [0, 0]);
    return [total[0] / open.length, total[1] / open.length];
  }

  private numberProperty(value: unknown, fallback: number): number {
    const converted = Number(value);
    return Number.isFinite(converted) ? converted : fallback;
  }

  private normalizeCallSign(callSign: string): string {
    return (callSign || '').trim().toUpperCase();
  }

  private formatModelClass(modelClass: FleetDrone['modelClass'] | undefined): string {
    return modelClass ? modelClass.replace('_', ' ') : 'UNSPECIFIED';
  }

  private formatNumber(value: number): string {
    return new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 }).format(value);
  }

  private escapeHtml(value: unknown): string {
    return String(value)
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }
}
