import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { BehaviorSubject } from 'rxjs';
import { AppComponent } from './app.component';
import { TelemetryService } from './services/telemetry.service';

// Stub TelemetryService so instantiating AppComponent does NOT open a real Firebase
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
});
