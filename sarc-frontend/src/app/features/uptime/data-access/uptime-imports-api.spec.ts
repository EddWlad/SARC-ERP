import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { UptimeImportsApi } from './uptime-imports-api';

describe('UptimeImportsApi', () => {
  let api: UptimeImportsApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(UptimeImportsApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uploads the weekly report as multipart with the monday of its week', () => {
    const file = new File(['x'], 'Informe_UpTime.xlsx');
    void firstValueFrom(api.upload('2026-10-19', file));

    const request = http.expectOne('/api/v1/uptime/imports');
    expect(request.request.method).toBe('POST');
    const body = request.request.body as FormData;
    expect(body.get('weekStart')).toBe('2026-10-19');
    expect(body.get('file')).toBe(file);
    request.flush({});
  });
  it('conserva metadata de referencia y ausencia de limites del servidor', async () => {
    const response = firstValueFrom(api.upload('2026-10-19', new File(['x'], 'Informe.xlsx')));
    const pending = http.expectOne('/api/v1/uptime/imports');
    pending.flush({
      id: 'b1',
      temporalPrecision: 'REFERENCE_WEEK',
      referenceWeek: '2026-10-19',
      declaredPeriodStart: null,
      declaredPeriodEnd: null,
      appliedRows: 0,
    });
    const batch = await response;
    expect(batch.temporalPrecision).toBe('REFERENCE_WEEK');
    expect(batch.referenceWeek).toBe('2026-10-19');
    expect(batch.declaredPeriodStart).toBeNull();
    expect(batch.appliedRows).toBe(0);
  });
});
