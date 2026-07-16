import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { DroneApiService } from './drone-api.service';

describe('DroneApi', () => {
  let service: DroneApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DroneApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('sends the complete typed dossier when deploying a unit', () => {
    const dossier = {
      callSign: 'SPECTRE-01',
      model: 'SPECTRE-IV',
      modelClass: 'RECON' as const,
      topSpeed: 245,
      radarRange: 32000,
      payloadCapacity: 2,
      batteryCapacity: 14000,
      visionModes: ['Standard', 'Thermal'],
    };

    service.create(dossier).subscribe();

    const request = http.expectOne('https://sentinel-api-kh1p.onrender.com/api/drones');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(dossier);
    request.flush({
      ...dossier,
      id: 'unit-1',
      status: 'OFFLINE',
      createdAt: '2026-07-15T12:00:00',
    });
  });

  it('synchronizes volumetric geofences and requests 3D routes', () => {
    const zones = [
      {
        id: 'nfz-1',
        polygon: [
          [44.4, 26.0],
          [44.4, 26.2],
          [44.5, 26.2],
        ],
        minAltitude: 100,
        maxAltitude: 500,
      },
    ];
    service.syncGeofences(zones).subscribe();
    const geofenceRequest = http.expectOne('http://localhost:8080/api/geofences');
    expect(geofenceRequest.request.method).toBe('POST');
    expect(geofenceRequest.request.body).toEqual(zones);
    geofenceRequest.flush(zones);

    service.requestRoute('SPECTRE-01', [44.4, 26.1, 150], [44.5, 26.2, 200]).subscribe();
    const routeRequest = http.expectOne('http://localhost:8080/api/navigation/route');
    expect(routeRequest.request.body).toEqual({
      callSign: 'SPECTRE-01',
      start: [44.4, 26.1, 150],
      end: [44.5, 26.2, 200],
    });
    routeRequest.flush([[44.5, 26.2, 200]]);
  });
});
