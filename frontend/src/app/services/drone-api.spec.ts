import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { DroneApiService } from './drone-api.service';

describe('DroneApi', () => {
  let service: DroneApiService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DroneApiService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
