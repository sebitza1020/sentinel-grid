export type DroneClass = 'RECON' | 'INTERCEPTOR' | 'HEAVY_SUPPORT';

export interface Drone {
  id: string;
  callSign: string;
  model: string;
  status: string;
  batteryCapacity: number;
  modelClass: DroneClass;
  topSpeed: number;
  radarRange: number;
  payloadCapacity: number;
  visionModes: string[];
  createdAt: string;
}

export interface DroneTelemetry {
  lat?: number;
  lng?: number;
  alt?: number;
  altitude?: number;
  battery?: number;
  batt?: number;
  report?: string;
  threat_level?: string;
  status?: string;
}

export type FleetDrone = Drone & DroneTelemetry;

export type TelemetrySnapshot = Record<string, DroneTelemetry>;

export type Position3D = [lat: number, lng: number, altitude: number];

export interface GeofenceVolume {
  id: string;
  polygon: number[][];
  minAltitude: number;
  maxAltitude: number;
}

export interface PathMessage {
  type: 'path';
  callSign: string;
  waypoints: Position3D[];
  reinforcement: boolean;
  rtb: boolean;
}

export interface TacticalRoute {
  callSign: string;
  waypoints: Position3D[];
  reinforcement: boolean;
  rtb: boolean;
}

export interface DroneCreateRequest {
  callSign: string;
  model: string;
  modelClass: DroneClass;
  topSpeed: number;
  radarRange: number;
  payloadCapacity: number;
  batteryCapacity: number;
  visionModes: string[];
}

export const DRONE_CLASSES: readonly DroneClass[] = ['RECON', 'INTERCEPTOR', 'HEAVY_SUPPORT'];

export const VISION_MODE_OPTIONS = [
  'Standard',
  'Thermal',
  'Infrared',
  'Low-Light',
  'Multispectral',
  'Hyperspectral',
] as const;

export function emptyDroneRequest(): DroneCreateRequest {
  return {
    callSign: '',
    model: '',
    modelClass: 'RECON',
    topSpeed: 0,
    radarRange: 0,
    payloadCapacity: 0,
    batteryCapacity: 0,
    visionModes: ['Standard'],
  };
}
