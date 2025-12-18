import { TestBed } from '@angular/core/testing';

import { DroneApiService } from './drone-api';

describe('DroneApi', () => {
  let service: DroneApiService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(DroneApiService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
