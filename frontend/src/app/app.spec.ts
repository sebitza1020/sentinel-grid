import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { BehaviorSubject } from 'rxjs';
import { AppComponent } from './app.component';
import { TelemetryService } from './services/telemetry.service';

// Stub TelemetryService so instantiating AppComponent does NOT open a real telemetry
// WebSocket (the real service connects in its constructor).
const telemetryStub = { dronePositions$: new BehaviorSubject<Record<string, unknown>>({}) };

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TelemetryService, useValue: telemetryStub },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    // createComponent (without detectChanges) does not run ngOnInit, so Leaflet's
    // map bootstrap is not triggered — we only assert the component wires up cleanly.
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('formats dossier values and escapes Leaflet popup content', () => {
    const component = TestBed.createComponent(AppComponent).componentInstance;

    expect(component.formatModelClass('HEAVY_SUPPORT')).toBe('HEAVY SUPPORT');
    expect(component.formatNumber(32000)).toBe('32,000');
    expect(component.escapeHtml('<img src=x onerror=alert(1)>')).toBe(
      '&lt;img src=x onerror=alert(1)&gt;',
    );
  });

  it('requires a complete dossier and toggles vision modes', () => {
    const component = TestBed.createComponent(AppComponent).componentInstance;
    expect(component.isDeployFormValid()).toBe(false);

    Object.assign(component.newDrone, {
      callSign: 'SPECTRE-01',
      model: 'SPECTRE-IV',
      topSpeed: 245,
      radarRange: 32000,
      payloadCapacity: 2,
      batteryCapacity: 14000,
    });
    expect(component.isDeployFormValid()).toBe(true);

    component.toggleVisionMode('Thermal');
    expect(component.newDrone.visionModes).toContain('Thermal');
    component.toggleVisionMode('Thermal');
    expect(component.newDrone.visionModes).not.toContain('Thermal');
  });
});
