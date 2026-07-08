import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { vi } from 'vitest';

// TelemetryService opens an RxJS webSocket in its constructor. Mock the factory so the test opens
// no real connection and runs deterministically (a plain Subject stands in for the socket).
vi.mock('rxjs/webSocket', () => ({
  webSocket: vi.fn(() => new Subject()),
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

  it('should start with an empty fleet snapshot', () => {
    expect(service.dronePositions$.value).toEqual({});
  });
});
