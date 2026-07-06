import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

// TelemetryService boots Firebase in its constructor (initializeApp + getDatabase +
// a live onValue subscription). Mock the Firebase SDK so the test opens no real
// connection and runs deterministically.
vi.mock('firebase/app', () => ({
  initializeApp: vi.fn(() => ({})),
}));
vi.mock('firebase/database', () => ({
  getDatabase: vi.fn(() => ({})),
  ref: vi.fn(() => ({})),
  onValue: vi.fn(),
  connectDatabaseEmulator: vi.fn(),
}));

import { TelemetryService } from './telemetry.service';

describe('Telemetry', () => {
  let service: TelemetryService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(TelemetryService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
