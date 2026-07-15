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
});
