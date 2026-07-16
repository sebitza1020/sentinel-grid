import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { BehaviorSubject, Subject } from 'rxjs';
import { AppComponent } from './app.component';
import { SpeechRecognitionFailure } from './services/speech-recognition.service';
import { SpeechRecognitionService } from './services/speech-recognition.service';
import { TelemetryService } from './services/telemetry.service';

// Stub TelemetryService so instantiating AppComponent does NOT open a real telemetry
// WebSocket (the real service connects in its constructor).
const telemetryStub = {
  dronePositions$: new BehaviorSubject<Record<string, unknown>>({}),
  dronePaths$: new Subject(),
};

let voiceTranscript$: Subject<string>;
const speechStub = {
  supported: true,
  startListening: vi.fn(() => voiceTranscript$.asObservable()),
  stopListening: vi.fn(),
  abortListening: vi.fn(),
};

describe('App', () => {
  beforeEach(async () => {
    voiceTranscript$ = new Subject<string>();
    speechStub.startListening.mockClear();
    speechStub.stopListening.mockClear();
    speechStub.abortListening.mockClear();
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: TelemetryService, useValue: telemetryStub },
        { provide: SpeechRecognitionService, useValue: speechStub },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    // createComponent (without detectChanges) does not run ngOnInit, so Leaflet's
    // map bootstrap is not triggered — we only assert the component wires up cleanly.
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('formats dossier values and escapes popup content', () => {
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

  it('captures a transcript and reports an accepted voice command', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const component = fixture.componentInstance;
    const http = TestBed.inject(HttpTestingController);

    component.toggleVoiceCommand();
    expect(component.voiceState).toBe('listening');
    voiceTranscript$.next('Send Razor-12 to Bucharest');
    expect(component.voiceState).toBe('processing');

    const request = http.expectOne('http://localhost:8080/api/fleet/command-voice');
    request.flush({
      action: 'MOVE',
      callSign: 'RAZOR-12',
      latitude: 44.4268,
      longitude: 26.1042,
      waypoints: [[44.4268, 26.1042, 150]],
    });

    expect(component.voiceState).toBe('success');
    expect(component.voiceTranscript).toBe('Send Razor-12 to Bucharest');
    expect(component.voiceStatusMessage).toContain('RAZOR-12');
  });

  it('stops on a second click and surfaces microphone permission errors', () => {
    const component = TestBed.createComponent(AppComponent).componentInstance;

    component.toggleVoiceCommand();
    component.toggleVoiceCommand();
    expect(speechStub.stopListening).toHaveBeenCalled();

    voiceTranscript$.error(
      new SpeechRecognitionFailure('permission', 'Microphone permission was denied.'),
    );
    expect(component.voiceState).toBe('error');
    expect(component.voiceStatusMessage).toContain('permission');
  });
});
