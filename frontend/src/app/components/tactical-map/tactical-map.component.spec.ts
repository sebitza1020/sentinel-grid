import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  isValidAltitudeBand,
  normalizeAltitude,
  TacticalMapComponent,
  toBackendPolygon,
  toGeoJsonRing,
} from './tactical-map.component';

describe('TacticalMapComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TacticalMapComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('creates without starting WebGL before view initialization', () => {
    const fixture = TestBed.createComponent(TacticalMapComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('normalizes telemetry altitude and validates NFZ bands', () => {
    expect(normalizeAltitude({ alt: 175, altitude: 900 })).toBe(175);
    expect(normalizeAltitude({ altitude: 220 })).toBe(220);
    expect(normalizeAltitude({ alt: -1 })).toBe(0);
    expect(isValidAltitudeBand(0, 500)).toBe(true);
    expect(isValidAltitudeBand(500, 100)).toBe(false);
  });

  it('converts coordinate order and closes GeoJSON polygon rings', () => {
    const backend = [
      [44.4, 26.0],
      [44.4, 26.2],
      [44.5, 26.2],
    ];
    const ring = toGeoJsonRing(backend);

    expect(ring[0]).toEqual([26.0, 44.4]);
    expect(ring[ring.length - 1]).toEqual(ring[0]);
    expect(toBackendPolygon(ring)).toEqual(backend);
  });
});
